package com.yoga.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @date 2021/7/12 17:43
 */
@Controller
public class FormCacheController {
    private HttpHeaders httpHeaders;
    public FormCacheController(){
         httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    }

    public String doCache(HttpServletRequest request, @PathVariable String vid){
        ExecutorService threadPool = Executors.newFixedThreadPool(Integer.parseInt("10"));
        for (int i = 0; i < 10; i++) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        sendPost("11111");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        return "success";
    }

    private MultiValueMap<String,String> getParam(String vid){
        LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("id","1");
        map.add("text","测试文本");
        return map;
    }
    private void sendPost(String vid) throws IOException {
        HttpEntity<MultiValueMap<String,String>>  param = new HttpEntity<>(getParam(vid), httpHeaders);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<byte[]> entity = restTemplate.postForEntity("www.baidu.com", param, byte[].class);

        File resultFile = new File("D:\\wav\\" + param.getBody().get("text").get(0) + ".wav");

        Path fileName = Paths.get(resultFile.toURI());
        if (entity.getBody()!=null){
            //写字节数组到文件中
            Files.write(fileName,entity.getBody(), StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }

    }
}
