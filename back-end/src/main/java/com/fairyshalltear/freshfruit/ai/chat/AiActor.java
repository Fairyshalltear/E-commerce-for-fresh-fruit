package com.fairyshalltear.freshfruit.ai.chat;

public record AiActor(String kind,Long buyerId,String guestHash,String ip,Long credentialVersion) {
    public AiActor(String kind,Long buyerId,String guestHash,String ip){this(kind,buyerId,guestHash,ip,null);}
    public boolean guest(){return kind.equals("GUEST");}
    public String identity(){return guest()?"GUEST:"+guestHash:"BUYER:"+buyerId;}
}
