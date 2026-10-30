package com.gt.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretHashingTest {
    private final SecretHashing hashing = new SecretHashing();

    @Test
    void createsUniqueOpaqueTokensAndStableHashes() {
        String first = hashing.randomToken("GT2D_");
        String second = hashing.randomToken("GT2D_");
        assertThat(first).startsWith("GT2D_").isNotEqualTo(second);
        assertThat(hashing.sha256(first)).hasSize(64).isEqualTo(hashing.sha256(first));
        assertThat(hashing.constantTimeEquals(hashing.sha256(first), hashing.sha256(first))).isTrue();
        assertThat(hashing.constantTimeEquals(hashing.sha256(first), hashing.sha256(second))).isFalse();
    }
}
