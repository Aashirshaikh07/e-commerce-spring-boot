package com.aashir.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ResetTokenGeneratorTest {

    private final ResetTokenGenerator generator = new ResetTokenGenerator();

    @Test
    void generatesDifferentTokensEachTime() {
        assertNotEquals(generator.generate(), generator.generate());
    }

    @Test
    void hashIsStableAndSixtyFourCharactersLong() {
        String token = generator.generate();
        assertEquals(generator.hash(token), generator.hash(token));
        assertEquals(64, generator.hash(token).length());
    }
}