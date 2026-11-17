package com.fairyshalltear.freshfruit.ai;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import com.fairyshalltear.freshfruit.ai.model.*;
import static org.junit.jupiter.api.Assertions.*;

class AiProviderTest {
    HttpServer server;ExecutorService executor;AiModelFactory factory;AiModelFactory.Clients clients;
    final ObjectMapper json=new ObjectMapper();final AtomicInteger mode=new AtomicInteger(),requests=new AtomicInteger(),redirected=new AtomicInteger();final AtomicBoolean cancelled=new AtomicBoolean();
    @BeforeEach void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("localhost",0),0);executor=Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});server.setExecutor(executor);
        server.createContext("/v1/chat/completions",this::chat);server.createContext("/v1/embeddings",this::embedding);
        server.createContext("/redirect-target",exchange->{redirected.incrementAndGet();respond(exchange,200,"{}");});server.start();
        var credentials=new AiCredentials(Base64.getEncoder().encodeToString(new byte[32]));factory=new AiModelFactory(credentials,new AiEndpointPolicy("",true));
        String base="http://localhost:"+server.getAddress().getPort()+"/v1";
        clients=factory.clients(new AiModelSettings(1,base,"mock",credentials.encrypt("sk-test-only"),base,"mock-vector",credentials.encrypt("sk-vector-test"),64,"{question}",true,AiDtos.Parameters.defaults()));
    }
    @AfterEach void close(){server.stop(0);executor.shutdownNow();}
    @Test void actualSpringAiClientsParseStructuredChineseStreamAndVectors(){
        mode.set(1);assertEquals("RECOMMEND",clients.chat().prompt().user("分类").call().entity(AiModelService.Intent.class).scene());
        mode.set(0);String text=factory.stream(clients,"简短中文","草莓怎么保存").map(r->r.getResult()==null?"":Objects.toString(r.getResult().getOutput().getText(),"")).collectList().block(Duration.ofSeconds(5)).stream().reduce("",String::concat);
        assertEquals("草莓冷藏保存",text);assertEquals(64,factory.embed(clients,"草莓").length);assertEquals(3,requests.get());
    }
    @Test void wrongVectorDimensionAndHttpFailureDoNotRetryImplicitly(){
        mode.set(2);assertThrows(RuntimeException.class,()->factory.embed(clients,"测试"));
        mode.set(3);int before=requests.get();assertThrows(RuntimeException.class,()->clients.chat().prompt().user("测试").call().content());assertEquals(before+1,requests.get());
    }
    @Test void cancellingSubscriptionClosesUpstreamSocket()throws Exception{
        mode.set(4);factory.stream(clients,"简短中文","停止测试").take(1).blockLast(Duration.ofSeconds(5));
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!cancelled.get()&&System.nanoTime()<until)Thread.sleep(20);
        assertTrue(cancelled.get(),"供应商应观察到流连接关闭");assertEquals(1,requests.get());
    }
    @Test void redirectsNeverForwardCredentialsToAnotherTarget(){
        mode.set(5);
        assertThrows(RuntimeException.class,()->clients.chat().prompt().user("重定向测试").call().content());
        assertThrows(RuntimeException.class,()->factory.stream(clients,"简短回答","重定向测试").blockLast(Duration.ofSeconds(5)));
        assertEquals(2,requests.get());assertEquals(0,redirected.get(),"同步和流式客户端均不得跟随重定向");
    }
    private void chat(HttpExchange exchange)throws java.io.IOException{
        requests.incrementAndGet();assertEquals("Bearer sk-test-only",exchange.getRequestHeaders().getFirst("Authorization"));JsonNode request=json.readTree(exchange.getRequestBody());
        if(mode.get()==5){exchange.getResponseHeaders().set("Location","http://localhost:"+server.getAddress().getPort()+"/redirect-target");exchange.sendResponseHeaders(302,-1);exchange.close();return;}
        if(mode.get()==3){respond(exchange,500,"{\"error\":{\"message\":\"模拟故障\"}}");return;}
        if(request.path("stream").asBoolean()){
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);
            try(var out=exchange.getResponseBody()){
                if(mode.get()==4){for(int i=0;i<200;i++){out.write(frame("安全测试文字".repeat(1000)).getBytes(StandardCharsets.UTF_8));out.flush();try{Thread.sleep(20);}catch(InterruptedException ignored){return;}}}
                else{for(String text:List.of("草莓","冷藏","保存")){byte[] bytes=frame(text).getBytes(StandardCharsets.UTF_8);for(byte value:bytes){out.write(value);out.flush();}}out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));}
            }catch(java.io.IOException error){cancelled.set(true);}finally{exchange.close();}
        }else{
            String content=mode.get()==1?"{\"scene\":\"RECOMMEND\"}":"连接成功";
            respond(exchange,200,json.writeValueAsString(Map.of("id","test","object","chat.completion","created",1,"model","mock","choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop")),"usage",Map.of("prompt_tokens",5,"completion_tokens",3,"total_tokens",8))));
        }
    }
    private void embedding(HttpExchange exchange)throws java.io.IOException{
        requests.incrementAndGet();exchange.getRequestBody().readAllBytes();List<Double> vector=new ArrayList<>();for(int i=0;i<(mode.get()==2?32:64);i++)vector.add(i==0?1d:0d);
        respond(exchange,200,json.writeValueAsString(Map.of("object","list","model","mock-vector","data",List.of(Map.of("object","embedding","index",0,"embedding",vector)),"usage",Map.of("prompt_tokens",5,"total_tokens",5))));
    }
    private String frame(String text)throws java.io.IOException{return "data: "+json.writeValueAsString(Map.of("id","stream","object","chat.completion.chunk","created",1,"model","mock","choices",List.of(Map.of("index",0,"delta",Map.of("role","assistant","content",text)))))+"\n\n";}
    private void respond(HttpExchange exchange,int status,String body)throws java.io.IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}exchange.close();}
}
