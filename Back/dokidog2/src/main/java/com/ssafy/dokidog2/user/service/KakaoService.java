package com.ssafy.dokidog2.user.service;

import com.ssafy.dokidog2.user.dto.KakaoDTO;
import com.ssafy.dokidog2.user.entity.User;
import com.ssafy.dokidog2.user.repository.UserRepository;
import com.ssafy.dokidog2.util.JwtTokenProvider;
import com.ssafy.dokidog2.util.UserGrade;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class KakaoService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    public KakaoService(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Value("${kakao.client.id}")
    private String KAKAO_CLIENT_ID;
    @Value("${kakao.client.secret}")
    private String KAKAO_CLIENT_SECRET;
    @Value("${kakao.redirect.url}")
    private String KAKAO_REDIRECT_URL;
    private final static String KAKAO_AUTH_URI = "https://kauth.kakao.com";
    private final static String KAKAO_API_URI = "https://kapi.kakao.com";

    public String getKakaoInfo(String code) throws Exception {
        if (code == null) {
            throw new Exception("Failed get authorization code");
        }

        String accessToken = "";
        String refreshToken = "";

        try {
            HttpHeaders headers = new HttpHeaders();
	        headers.add("Content-type", "application/x-www-form-urlencoded");

	        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
	        params.add("grant_type"   , "authorization_code");
	        params.add("client_id"    , KAKAO_CLIENT_ID);
	        params.add("client_secret", KAKAO_CLIENT_SECRET);
	        params.add("code"         , code);
	        params.add("redirect_uri" , KAKAO_REDIRECT_URL);

	        RestTemplate restTemplate = new RestTemplate();
	        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(params, headers);

	        ResponseEntity<String> response = restTemplate.exchange(
	        		KAKAO_AUTH_URI + "/oauth/token",
	                HttpMethod.POST,
	                httpEntity,
	                String.class
	        );

	        JSONParser jsonParser = new JSONParser();
	        JSONObject jsonObj = (JSONObject) jsonParser.parse(response.getBody());
            log.info("kakao json : "+ jsonObj);

            accessToken  = (String) jsonObj.get("access_token");
            refreshToken = (String) jsonObj.get("refresh_token");
        } catch (Exception e) {
            throw new Exception("API call failed");
        }
        return getUserInfoWithToken(accessToken);
    }

    private String getUserInfoWithToken(String accessToken) throws Exception {
        //HttpHeader 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        //HttpHeader 담기
        RestTemplate rt = new RestTemplate();
        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = rt.exchange(
                KAKAO_API_URI + "/v2/user/me",
                HttpMethod.POST,
                httpEntity,
                String.class
        );

        //Response 데이터 파싱
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObj    = (JSONObject) jsonParser.parse(response.getBody());
        System.out.println(jsonObj);

        String id = Long.toString((long) jsonObj.get("id"));

        Long checkId = userRepository.findByCompanyIdAndCompanyName(id, "kakao");
        long time = System.currentTimeMillis();
        if (checkId == null) {
            User user = new User();
            user.setCompanyId(id);
            user.setCompanyName("kakao");
            user.setGrade(UserGrade.TEMPORARY);
            user.setRegDttm(LocalDateTime.now());
            User savedUser = userRepository.save(user);

            System.out.println(savedUser);
            long userId = savedUser.getUserId();
            UserGrade grade = savedUser.getGrade();
            return jwtTokenProvider.createAccessToken(userId, grade, time);
        }
        else {
            User user = userRepository.findByUserId(checkId);
            long userId = user.getUserId();
            UserGrade grade = user.getGrade();
            return jwtTokenProvider.createAccessToken(userId, grade, time);
        }
    }
}
