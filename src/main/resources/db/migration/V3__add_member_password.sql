-- JWT 인증 도입에 따른 회원 비밀번호 컬럼 추가
--
-- BCrypt 해시는 항상 60자다. 알고리즘·비용·솔트가 해시 문자열 안에 함께 들어 있어서
-- 별도 솔트 컬럼이 필요 없다.

ALTER TABLE member
    ADD COLUMN password VARCHAR(60) NOT NULL DEFAULT '' AFTER email;

-- 샘플 회원의 비밀번호: password1234
-- (BCrypt, cost 10. 개발용이므로 그대로 두고, 실제 서비스라면 절대 커밋하지 않는다.)
UPDATE member
   SET password = '$2b$10$3JGjbhpIcryTv0pQPLvr/eHhzq1RqFMuVlTXt/2bnGsZVXuIvoqMe'
 WHERE id = 1;

-- 기본값은 최초 적용을 위한 장치였으므로 제거한다.
-- 이후 INSERT 는 반드시 비밀번호를 명시해야 한다.
ALTER TABLE member
    ALTER COLUMN password DROP DEFAULT;
