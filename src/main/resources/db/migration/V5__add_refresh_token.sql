-- 리프레시 토큰
--
-- 액세스 토큰은 서명만 맞으면 통과하므로 서버가 중간에 취소할 수 없다.
-- 로그아웃과 토큰 재발급을 다루려면 "서버가 기억하는 토큰"이 하나는 있어야 하고,
-- 그 역할을 이 테이블이 맡는다.
--
-- 왜 토큰 원문이 아니라 해시인가
--   이 테이블이 통째로 새면 저장된 값으로 바로 로그인할 수 있게 된다.
--   비밀번호와 같은 이유로 원문을 두지 않는다. 다만 BCrypt 가 아니라 SHA-256 인데,
--   리프레시 토큰은 사람이 고른 문자열이 아니라 난수 32바이트라 사전 공격 대상이 아니고,
--   매 재발급마다 조회 키로 쓰여야 해서 (해시가 같아야 찾을 수 있다) 솔트를 쓸 수 없다.
--   SHA-256 hex = 64자 고정이므로 CHAR(64), 16진수뿐이니 ascii 로 둔다.
--
-- 왜 지우지 않고 revoked_at 을 남기는가
--   회전(rotation) 후에도 옛 토큰을 남겨 두면 "이미 쓴 토큰이 다시 왔다" 를 구분할 수 있다.
--   행이 없으면 그냥 위조고, 행이 있는데 revoked_at 이 차 있으면 탈취 의심 신호다.
--   (없는 토큰과 폐기된 토큰을 클라이언트에게는 똑같이 401 로 답한다.)

CREATE TABLE refresh_token
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    member_id  BIGINT   NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_member (member_id),
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
