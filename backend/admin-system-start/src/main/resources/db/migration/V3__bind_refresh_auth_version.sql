-- Bind every refresh record to the authorization version at issuance.
ALTER TABLE auth_refresh_session ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 AFTER user_id;
UPDATE auth_refresh_session s JOIN sys_user u ON u.id=s.user_id SET s.auth_version=u.auth_version;
ALTER TABLE auth_refresh_session ADD CONSTRAINT chk_refresh_auth_version CHECK (auth_version >= 0);
