package com.fairyshalltear.freshfruit.ai.model;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import io.netty.channel.ChannelOption;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import static com.fairyshalltear.freshfruit.ai.AiErrors.*;

@Component
public class AiModelFactory {
    public record Clients(ChatClient chat, EmbeddingModel embedding, AiModelSettings settings) {}
    private final AiCredentials credentials;
    private final AiEndpointPolicy policy;
    private final Map<Long,Clients> cache = new LinkedHashMap<>(16,.75f,true);
    public AiModelFactory(AiCredentials credentials, AiEndpointPolicy policy) { this.credentials=credentials;this.policy=policy; }
    public synchronized void evict(long id) { cache.remove(id); }
    public synchronized Clients clients(AiModelSettings settings) {
        Clients result=cache.get(settings.id()); if(result!=null) return result;
        if(!settings.enabled()) throw unavailable("AI_DISABLED","在线模型未开启");
        ChatClient chat=null; EmbeddingModel embedding=null;
        RetryTemplate retry=RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build();
        if(settings.chatCipher()!=null) {
            OpenAiChatModel model=OpenAiChatModel.builder().openAiApi(api(settings.chatBaseUrl(),settings.chatCipher(),settings))
                .defaultOptions(OpenAiChatOptions.builder().model(settings.chatModel()).temperature(settings.parameters().temperature().doubleValue())
                    .maxTokens(settings.parameters().maxOutputTokens()).internalToolExecutionEnabled(false).build())
                .retryTemplate(retry).build();
            chat=ChatClient.builder(model).build();
        }
        if(settings.embeddingCipher()!=null && !settings.embeddingModel().isBlank()) {
            embedding=new OpenAiEmbeddingModel(api(settings.embeddingBaseUrl(),settings.embeddingCipher(),settings),MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(settings.embeddingModel()).dimensions(settings.dimensions()).encodingFormat("float").build(),retry);
        }
        result=new Clients(chat,embedding,settings);cache.put(settings.id(),result);
        if(cache.size()>8) cache.remove(cache.keySet().iterator().next());
        return result;
    }
    private OpenAiApi api(String baseUrl,String cipher,AiModelSettings settings) {
        String base=policy.validate(baseUrl).toString().replaceAll("/+$","");
        HttpClient transport=HttpClient.newConnection().resolver(io.netty.resolver.DefaultAddressResolverGroup.INSTANCE).followRedirect(false)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,settings.parameters().connectTimeoutSeconds()*1000)
            .responseTimeout(Duration.ofSeconds(settings.parameters().totalTimeoutSeconds()))
            .doOnConnected(connection->{
                // Verify the actual socket before sending credentials, including DNS changes since configuration.
                if(!(connection.channel().remoteAddress() instanceof InetSocketAddress target)) throw invalid("模型网络目标无效");
                policy.validateAddress(target.getAddress());
            });
        ReactorClientHttpRequestFactory requests=new ReactorClientHttpRequestFactory(transport);
        requests.setReadTimeout(Duration.ofSeconds(settings.parameters().totalTimeoutSeconds()));
        RestClient.Builder sync=RestClient.builder().requestFactory(requests).requestInterceptor((request,body,execution)->{
            policy.validate(request.getURI().toString()); return execution.execute(request,body);
        });
        WebClient.Builder async=WebClient.builder().clientConnector(new ReactorClientHttpConnector(transport))
            .filter((request,next)->{policy.validate(request.url().toString());return next.exchange(request).flatMap(response->response.statusCode().value()>=300 ? response.releaseBody().then(reactor.core.publisher.Mono.error(unavailable("AI_PROVIDER_HTTP_ERROR","模型服务返回错误，请核对配置或配额"))) : reactor.core.publisher.Mono.just(response));});
        return OpenAiApi.builder().baseUrl(base).completionsPath("/chat/completions").embeddingsPath("/embeddings")
            .apiKey(credentials.decrypt(cipher)).restClientBuilder(sync).webClientBuilder(async)
            .responseErrorHandler(new org.springframework.web.client.ResponseErrorHandler(){
                @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response)throws java.io.IOException{return response.getStatusCode().value()>=300;}
                @Override public void handleError(java.net.URI uri,org.springframework.http.HttpMethod method,org.springframework.http.client.ClientHttpResponse response)throws java.io.IOException{throw unavailable("AI_PROVIDER_HTTP_ERROR","模型服务返回错误，请核对配置或配额");}
            }).build();
    }
    public float[] embed(Clients clients,String text) {
        if(clients.embedding()==null) throw unavailable("AI_EMBEDDING_UNAVAILABLE","向量服务尚未配置");
        float[] vector=clients.embedding().embed(text);
        if(vector.length!=clients.settings().dimensions()) throw unavailable("AI_VECTOR_DIMENSIONS","向量维度与配置不一致");
        for(float component:vector) if(!Float.isFinite(component)) throw unavailable("AI_VECTOR_INVALID","向量响应无效");
        return vector;
    }
    public Flux<ChatResponse> stream(Clients clients,String system,String user) {
        if(clients.chat()==null) return Flux.error(unavailable("AI_CHAT_UNAVAILABLE","生成服务尚未配置"));
        return clients.chat().prompt().system(system).user(user).stream().chatResponse();
    }
}
