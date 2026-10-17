package ca.saboor.lumiguide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ButtonIconTest {
    @Test void selectsAtlasAcrossItemTextureMove() {
        assertEquals("minecraft:blocks", ButtonIcon.spriteAtlas("1.21.10").asString());
        assertEquals("minecraft:items", ButtonIcon.spriteAtlas("1.21.11").asString());
        assertEquals("minecraft:items", ButtonIcon.spriteAtlas("1.22").asString());
        assertEquals("minecraft:items", ButtonIcon.spriteAtlas("26.1").asString());
    }
}
