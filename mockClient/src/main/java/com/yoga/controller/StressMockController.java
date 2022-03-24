package com.yoga.controller;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @date 2021/7/9 9:07
 */
@Controller
public class StressMockController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StressMockController.class);
    public static List<String> sendMsgList = new ArrayList<>();
    public static AtomicInteger indexOfCell = new AtomicInteger(0);
    public static AtomicInteger currentThreadCount = new AtomicInteger(0);

    private static boolean initFlag = false;

    public static String excelPath;
    public static int threadNum;

    @RequestMapping("/stress")
    @ResponseBody
    public String httpStressTest(HttpServletRequest request) {
        LOGGER.info("method begin");
        if (initFlag) {
            LOGGER.info("already exist.pls shutdown the old one");
            return "pls shutdown the old one";
        }
        initFlag = true;

        excelPath = request.getParameter("excelPath");
        threadNum = Integer.parseInt(request.getParameter("threadNum"));


        boolean readExcelResult = readExcel();
        if (readExcelResult) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    sendRequest();
                }
            });
            thread.start();
        }
        return "success";
    }

    private static synchronized JSONObject assembleParam() {
        String cellValue = null;
        int count = indexOfCell.getAndIncrement();
        if (count < sendMsgList.size()) {
            cellValue = sendMsgList.get(count);
        } else {
            //返回回到第一行，实现循环功能
            indexOfCell = new AtomicInteger(0);
            cellValue = sendMsgList.get(indexOfCell.getAndIncrement());
        }
        String[] split = cellValue.split("=&=");
        JSONObject assembleParam = new JSONObject();
        assembleParam.put("id", split[0]);
        assembleParam.put("text", split[1]);
        return assembleParam;
    }

    private boolean readExcel() {
        File file = new File(excelPath);
        FileInputStream fis = null;
        Workbook workbook = null;
        if (file.exists()) {
            try {
                LOGGER.info("读取excel文件[{}]", excelPath);
                fis = new FileInputStream(file);
                workbook = WorkbookFactory.create(fis);
                Sheet sheet0 = workbook.getSheetAt(0);
                //获取当前sheet总行数
                int rows = sheet0.getPhysicalNumberOfRows();
                for (int i = 0; i < rows; i++) {
                    Row row = sheet0.getRow(i);
                    Cell cell0 = row.getCell(0);
                    cell0.setCellType(Cell.CELL_TYPE_STRING);
                    String id = cell0.getStringCellValue();
                    Cell cell1 = row.getCell(1);
                    cell0.setCellType(Cell.CELL_TYPE_STRING);
                    String text = cell0.getStringCellValue();
                    sendMsgList.add(id + "=&=" + text);
                }
                LOGGER.info("excel文件读取完毕");
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fis != null) fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            LOGGER.error("excel文件不存在 [{}]", excelPath);
        }
        return false;
    }

    public void sendRequest() {
        //开关
        while (true) {
            if (currentThreadCount.get() < threadNum) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        sendHttpRequest(assembleParam());
                    }
                });
                thread.start();
            }
        }

    }

    private void sendHttpRequest(JSONObject assembleParam) {
        int incrementCurrency = currentThreadCount.incrementAndGet();
        LOGGER.info("send http start ,the currency is [{}] now", incrementCurrency);
        Map<String, String> resultMap = null;
        try {
            resultMap = synthesizeAsync(assembleParam);
            if (resultMap != null) {
                String responseCode = resultMap.get("responseCode");
                String responseMsg = resultMap.get("responseMsg");
                if ("400".equals(responseCode) || "500".equals(responseCode)) {
                    LOGGER.error("请求响应失败");
                }
                if ("200".equals(responseCode)) {
                    //等50ms再发起第二个请求
                    Thread.sleep(50);
                    if (!Strings.isNullOrEmpty(responseMsg)) {
                        getSecondResult(resultMap.get("requestId"));
                    }
                }
            } else {
                LOGGER.info("请求任务失败");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            int decrementCurrency = currentThreadCount.decrementAndGet();
            LOGGER.info("send http end , the currency is [{}] now", decrementCurrency);
        }
    }

    private Map<String, String> synthesizeAsync(JSONObject assembleParam) throws IOException {
        HashMap<String, String> resultMap = new HashMap<>();
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        try {
            httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost("https://www.baidu.com" + "/Async");
            httpPost.addHeader("Content-type", "application/json; charset=UTF-8");
            StringEntity entity = new StringEntity(assembleParam.toString(), StandardCharsets.UTF_8);
            entity.setContentType("text/json");
            entity.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));

            httpPost.setEntity(entity);
            response = httpClient.execute(httpPost);
            //响应状态码
            int responseStatusCode = response.getStatusLine().getStatusCode();
            //这里也可以遍历响应头
            //Header[] allHeaders = response.getAllHeaders();
            //响应体
            HttpEntity responseEntity = response.getEntity();
            String content = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);

            if (Strings.isNullOrEmpty(content)) {
                String ttsRequestId = JSONObject.parseObject(content).getString("ttsRequestId");
                resultMap.put("ttsRequestId", ttsRequestId);
                return resultMap;
            } else {
                LOGGER.error("第一次请求返回结果content为空");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
            if (httpClient != null) {
                httpClient.close();
            }
        }
        return null;
    }

    /**
     * 第二次请求获取结果
     *
     * @param requestId 成功失败
     */
    private void getSecondResult(String requestId) {
        boolean endFlag = true;
        boolean getSuccessFlag = false;
        ArrayList<byte[]> resultAudioList = new ArrayList<>();
        //整个获取动作开始时间
        Long firstTime = 0L;
        Long lastTime = 0L;
        boolean firstGetResultFlag = false;
//        int totalGetTime = 0;
        while (endFlag) {
//            if (totalGetTime >= 30){
//                endFlag = false;
//                break;
//            }
//            totalGetTime ++;
            CloseableHttpClient httpClient = null;
            CloseableHttpResponse response = null;
            InputStream inputStream = null;
            CloseableHttpClient httpClient2 = null;
            try {
                httpClient2 = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost("https://www.taobao.com" + "/second");
                httpPost.addHeader("Content-type", "application/json; charset=UTF-8");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("ttsRequestId", requestId);
                StringEntity entity = new StringEntity(jsonObject.toString(), StandardCharsets.UTF_8);
                entity.setContentType("text/json");
                entity.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));

                httpPost.setEntity(entity);
                response = httpClient2.execute(httpPost);

                int statusCode = response.getStatusLine().getStatusCode();

                long endTimeMillis = System.currentTimeMillis();
                /**
                 * 语音合成结果，返回PCM
                 * 200 取数据正常且已经取完
                 * 206 只取了部分
                 * 204 未取到
                 * 410 数据不存在或者已过期
                 */
                //请求成功
                if (statusCode == 200) {
                    String responseCode = null;
                    String responseMsg = null;
                    int ttsContentLength = 0;
                    int ttsSoundDuration = 0;
                    Header[] allHeaders = response.getAllHeaders();
                    for (Header header : allHeaders) {
                        String headerName = header.getName();
                        String headerValue = header.getValue();
                        if ("responseCode".equals(headerName)) {
                            responseCode = headerValue;
                        }
                        if ("TTS-SoundDuration".equals(headerName)) {
                            if (Strings.isNullOrEmpty(headerValue)) {
                                ttsContentLength = Integer.parseInt(headerValue);
                            }
                        }
                    }
                    if ("200".equals(responseCode)) {
                        InputStream inputStreamAudio = response.getEntity().getContent();
                        byte[] bytes = readInputStream(inputStreamAudio);
                        LOGGER.info("本次获取音频流长度[{}]", bytes.length);
                        resultAudioList.add(bytes);

                        if (!firstGetResultFlag){
                            firstGetResultFlag = true;
                            firstTime = endTimeMillis;
                        }
                        lastTime = endTimeMillis;
                        getSuccessFlag = true;
                        inputStreamAudio.close();
                    }
                    if ("206".equals(responseCode)) {
                        InputStream inputStreamAudio = response.getEntity().getContent();
                        byte[] bytes = readInputStream(inputStreamAudio);
                        LOGGER.info("本次获取音频流长度[{}]", bytes.length);
                        resultAudioList.add(bytes);

                        if (!firstGetResultFlag){
                            firstGetResultFlag = true;
                            firstTime = endTimeMillis;
                        }
                        Thread.sleep(50);
                    }
                    if ("206".equals(responseCode)) {
                        //数据不存在或者已过期
                        endFlag = false;
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        endFlag = false;
    }

    public static byte[] readInputStream(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        byte[] bytes = bos.toByteArray();
        bos.close();
        return bytes;
    }
}
