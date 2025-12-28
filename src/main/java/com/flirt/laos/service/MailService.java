package com.flirt.laos.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
    private static String number;
    
    private final JavaMailSender javaMailSender;
    
    @Value("${spring.mail.username}")
    private String senderEmail;

    public static void createNumber(){
        Random random = new Random();
        StringBuffer key = new StringBuffer();

        for(int i=0; i<6; i++){
            int idx = random.nextInt(3); // 0~2까지 랜덤 하게 생성

            //0, 1, 2값을 switch case 로 섞어서 분배
            switch(idx){
                case 0:
                    key.append((char) (random.nextInt(26) + 97)); //a~z까지 랜덤생성 후 key에 추가
                    break;
                case 1:
                    key.append((char) (random.nextInt(26) + 65)); //A~Z까지 랜덤생성 후 key에 추가
                    break;
                case 2:
                    key.append(random.nextInt(9)); // 0~9까지 랜덤 생성 후 key에 추가
                    break;
            }
        }
        number = key.toString();
    }

    public MimeMessage createMessage(String email){
        createNumber();
        log.info("Number : {}", number);
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try{
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8"); // Helper 사용
            messageHelper.setFrom(senderEmail);
            messageHelper.setTo(email);
            messageHelper.setSubject("[Travel] 이메일 인증 번호 발송");

            String body = "<html><body>";
            body += "<h3>[Travel 이메일 주소 인증]</h3>";
            body += "<p>안녕하세요? Travel 관리자 입니다.</p>";
            body += "<p>Travel 서비스 사용을 위해 회원가입 시 고객님께서 입력하신 이메일 주소의 인증이 필요합니다.<br>";
            body += "하단의 인증 번호로 이메일 인증을 완료하시면, 정상적으로 Travel 서비스를 이용하실 수 있습니다.</p>";
            body += "<div style='padding: 15px; background-color: #f4f4f4; text-align: center; font-size: 20px; font-weight: bold;'>";
            body += "인증 번호: " + number;
            body += "</div>";
            body += "<p>항상 최선의 노력을 다하는 Travel이 되겠습니다.<br>감사합니다.</p>";
            body += "</body></html>";
            messageHelper.setText(body, true);
        }catch (MessagingException e){
            e.printStackTrace();
        }
        return mimeMessage;
    }
    public String sendMail(String email) {
        MimeMessage mimeMessage = createMessage(email);
        log.info("[Mail 전송 시작]");
        javaMailSender.send(mimeMessage);
        log.info("[Mail 전송 완료]");
        return number;
    }
}
