package com.mapzen.open.util;

import android.util.Base64;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class SimpleCrypt {
    private static String defaultSalt = "default";
    private Cipher ecipher, dcipher;

    // static {
    //     try {
    //         System.loadLibrary("leyndo");
    //     } catch (UnsatisfiedLinkError e) {
    //         if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
    //             throw e;
    //         }
    //     }
    // }

    public native String getSalt();
    public char[] getSaltMix() {
        // if ("Dalvik".equals(System.getProperty("java.vm.name"))) {
        //     return getSalt().toCharArray();
        // } else {
        return defaultSalt.toCharArray();
	//}
    }

    public static SimpleCrypt withSpecialSalt(String specialSalt) {
        SimpleCrypt.defaultSalt = specialSalt;
        SimpleCrypt simpleCrypt = new SimpleCrypt();
        return simpleCrypt;
    }

    public SimpleCrypt() {
        byte[] salt = {
                (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32,
                (byte) 0x56, (byte) 0x34, (byte) 0xE3, (byte) 0x03
        };
        int iterationCount = 19;
        try {
            KeySpec keySpec = new PBEKeySpec(getSaltMix(), salt, iterationCount);
            SecretKey key =
                    SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(keySpec);
            ecipher = Cipher.getInstance(key.getAlgorithm());
            dcipher = Cipher.getInstance(key.getAlgorithm());
            AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);
            ecipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            dcipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
        } catch (Exception e) {
            Logger.e("SimpleCrypt error: " + e.getMessage());
        }
    }

    public String encode(String str) {
        try {
            byte[] utf8 = str.getBytes("UTF8");
            byte[] enc = ecipher.doFinal(utf8);
            return Base64.encodeToString(enc, Base64.DEFAULT);
        } catch (Exception e) {
            Logger.e("encoding error: " + e.getMessage());
        }
        return null;
    }

    public String decode(String gargle) {
        byte[] encodedBytes = Base64.decode(gargle, Base64.DEFAULT);
        try {
            byte[] utf8 = dcipher.doFinal(encodedBytes);
            return new String(utf8, "UTF8");
        } catch (Exception e) {
            Logger.e("decoding error: " + e.getMessage());
        }
        return null;
    }
}
