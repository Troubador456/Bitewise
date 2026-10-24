CREATE TABLE auth_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    auth_version INTEGER NOT NULL,
    CONSTRAINT uk_auth_users_email UNIQUE (email)
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_auth_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id)
        REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX ix_auth_sessions_user_id ON auth_sessions (user_id);
CREATE INDEX ix_auth_sessions_family_id ON auth_sessions (family_id);
CREATE INDEX ix_auth_sessions_expires_at ON auth_sessions (expires_at);

CREATE TABLE auth_action_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL,
    token_type VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_auth_action_tokens_user FOREIGN KEY (user_id)
        REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX ix_auth_action_tokens_user_id ON auth_action_tokens (user_id);
CREATE INDEX ix_auth_action_tokens_expires_at ON auth_action_tokens (expires_at);

CREATE TABLE food_profiles (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT fk_food_profiles_owner FOREIGN KEY (owner_id)
        REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX ix_food_profiles_owner_id ON food_profiles (owner_id);

CREATE TABLE food_profile_allergies (
    profile_id UUID NOT NULL,
    allergen_tag VARCHAR(255) NOT NULL,
    CONSTRAINT pk_food_profile_allergies PRIMARY KEY (profile_id, allergen_tag),
    CONSTRAINT fk_food_profile_allergies_profile FOREIGN KEY (profile_id)
        REFERENCES food_profiles (id) ON DELETE CASCADE
);

CREATE TABLE food_profile_restrictions (
    profile_id UUID NOT NULL,
    restriction VARCHAR(32) NOT NULL,
    CONSTRAINT pk_food_profile_restrictions PRIMARY KEY (profile_id, restriction),
    CONSTRAINT fk_food_profile_restrictions_profile FOREIGN KEY (profile_id)
        REFERENCES food_profiles (id) ON DELETE CASCADE
);
