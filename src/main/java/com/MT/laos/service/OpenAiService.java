package com.MT.laos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiService {

    @Value("${openai.api-key}")
    private String apiKey;

    private String defalut_prompt = """
            역할 : 너는 각 mbti별 한국 여행지 추천 및 하루 계획표 짜주는 ai야
            형식 : 답변형식은 json형식으로 하고, 특수문자 및 기호는 사용하지 않고 존댓말로 답변해. 한국에서 유명한 관광지를 추천해줘. 여행지는 3개만 알려줘
            예시 : 그리고 답변의 예시는 이런식으로 되었으면 좋겠어.
            1. 경주 - 경주월드
            추천이유:
            2. 부산 - 광안대교
            추천이유: 야경을 보며 사진을 찍고싶은 사람들을 위한 장소입니다.
            연인과 함께가기 추천
            3. 경주 - 불국사
            추천이유: 
            대한민국 역사에 관심있는 사람들을 위한 ~~~
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getDefalut_prompt(){
        return defalut_prompt;
    }

    public OpenAiService(RestTemplate restTemplate){
        this.restTemplate = restTemplate;
    }
    public String getContent(String default_prompt, String prompt, String mbti){
        String url = "https://api.openai.com/v1/chat/completions"; //api 호출 url
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(apiKey); //apiKey를 헤더에 삽입
        httpHeaders.setContentType(MediaType.APPLICATION_JSON); //JSON형식

        // MBTI 정보를 포함한 prompt 생성
        String promptWithMbti;
        if (mbti != null && !mbti.isEmpty()) {
            promptWithMbti = String.format("사용자의 MBTI는 %s입니다. %s", mbti, prompt);
        } else {
            promptWithMbti = prompt;
        }
        
        // default_prompt를 system 메시지로, prompt를 user 메시지로 전달
        String requestBody = String.format(
            "{\"model\":\"gpt-3.5-turbo\", \"messages\":[" +
            "{\"role\":\"system\", \"content\":\"%s\"}," +
            "{\"role\":\"user\", \"content\":\"%s\"}" +
            "]}",
            default_prompt.replace("\"", "\\\"").replace("\n", "\\n"),
            promptWithMbti.replace("\"", "\\\"").replace("\n", "\\n")
        );

        HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, httpEntity, String.class);
        String body = response.getBody();
        if (body == null) {
            throw new RuntimeException("OpenAI API 응답이 null입니다.");
        }
        
        // JSON 파싱하여 content만 추출
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            
            // choices 배열 확인
            JsonNode choices = jsonNode.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new RuntimeException("OpenAI API 응답에 choices 배열이 없거나 비어있습니다.");
            }
            
            // 첫 번째 choice의 message 확인
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.path("message");
            if (message.isMissingNode()) {
                throw new RuntimeException("OpenAI API 응답에 message 필드가 없습니다.");
            }
            
            // content 확인
            JsonNode contentNode = message.path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new RuntimeException("OpenAI API 응답에 content 필드가 없습니다.");
            }
            
            String content = contentNode.asText();
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("OpenAI API 응답의 content가 비어있습니다.");
            }
            
            return content;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("OpenAI API 응답 JSON 파싱 실패: " + e.getMessage() + "\n응답 본문: " + body, e);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI API 응답 처리 중 오류 발생: " + e.getMessage() + "\n응답 본문: " + body, e);
        }
    }
}
