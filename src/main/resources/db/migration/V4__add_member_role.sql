-- 관리자 역할 도입
--
-- 지금까지 인증(누구인가)만 다뤘고 인가(무엇을 할 수 있는가)는 없었다.
-- 상품 등록/수정/삭제가 로그인만 하면 누구나 가능한 상태를 이 컬럼으로 정리한다.

ALTER TABLE member
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER name;

-- password 와 달리 기본값을 남겨 둔다.
-- 회원가입은 언제나 USER 이고, 관리자는 별도 경로로만 만들어지기 때문이다.
-- 기본값이 있으면 INSERT 하는 쪽이 역할을 몰라도 안전한 값으로 떨어진다.

ALTER TABLE member
    ADD CONSTRAINT ck_member_role CHECK (role IN ('USER', 'ADMIN'));

-- 개발용 관리자 계정. 비밀번호는 샘플 회원과 같은 password1234 다.
INSERT INTO member (id, email, password, name, role)
VALUES (3, 'admin@theres.co',
        '$2b$10$3JGjbhpIcryTv0pQPLvr/eHhzq1RqFMuVlTXt/2bnGsZVXuIvoqMe',
        '관리자', 'ADMIN');
