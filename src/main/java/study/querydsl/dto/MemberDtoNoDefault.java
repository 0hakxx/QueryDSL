package study.querydsl.dto;

import lombok.Data;

/**
 * 기본 생성자가 없는 DTO - Setter/Field 테스트용
 */
@Data
public class MemberDtoNoDefault {
    private String username;
    private int age;

    // 기본 생성자 없음 - 파라미터 생성자만 존재
    public MemberDtoNoDefault(String username, int age) {
        this.username = username;
        this.age = age;
    }
}

