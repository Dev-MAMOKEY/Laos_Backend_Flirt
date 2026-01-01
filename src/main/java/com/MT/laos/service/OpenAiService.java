package com.MT.laos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiService {

    @Value("${OPENAI_API_KEY}")
    private String apiKey;

    private String defalut_prompt = """
            역할 : 너는 각 mbti별 한국 여행지 추천 및 하루 계획표 짜주는 ai야
            형식 : 답변형식은 json형식으로 하고, 특수문자 및 기호는 사용하지 않고 존댓말로 답변해. 한국에서 유명한 관광지를 추천해줘. 여행지는 3개만 알려줘
            예시 : 그리고 답변의 예시는 이런식으로 되었으면 좋겠어.
            [
              {
                "travel_destination": "서울 - 홍대",
                "recommend_reason": "(사용자mbti값 반환) 유형의 사람들에게는 역동적이고 다채로운 문화와 예술이 공존하는 홍대가 추천됩니다. 거리 곳곳에 아트 갤러리, 상점, 카페 등이 위치해 창의적인 자극을 받을 수 있습니다."
              },
              {
                "travel_destination": "전주 한옥마을",
                "recommend_reason": "(사용자mbti값 반환) 유형의 사람들에게는 조용하고 아름다운 전통 한옥마을이 추천됩니다. 한복을 대여하여 한국 전통 문화를 경험하고 예쁜 사진을 찍을 수 있습니다."
              },
              {
                "travel_destination": "제주도 - 성산일출봉",
                "recommend_reason": "(사용자mbti값 반환) 유형의 사람들에게는 독특한 지형과 아름다운 일출을 감상할 수 있는 성산일출봉이 추천됩니다. 모험을 즐기고 자연 속에서 아이디어를 발전시킬 수 있는 좋은 장소입니다."
              }
            ]
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
