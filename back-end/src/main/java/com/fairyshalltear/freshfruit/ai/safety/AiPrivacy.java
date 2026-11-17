package com.fairyshalltear.freshfruit.ai.safety;

public final class AiPrivacy {
    private AiPrivacy(){}
    public static String external(String text){return text==null?null:mask(text).replaceAll("(?i)(?:FF|AS)[0-9]{13}[0-9A-F]{8}","[业务单号已隐藏]");}
    public static String mask(String text){
        if(text==null)return null;
        return text.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~-]+","[凭据已隐藏]")
            .replaceAll("(?i)\\bsk-[A-Za-z0-9_-]{8,}\\b","[Key已隐藏]")
            .replaceAll("[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}","[凭据已隐藏]")
            .replaceAll("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)","[手机号已隐藏]")
            .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[邮箱已隐藏]")
            .replaceAll("[^\\s，。；：]{0,25}(?:省|市|区|县)[^\\s，。；：]{1,40}(?:路|街|巷|小区)[^\\s，。；：]{0,30}(?:号|室|栋)","[地址已隐藏]");
    }
}
