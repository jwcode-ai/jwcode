package com.jwcode.core.message;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tombstone 消息测试 — 测试用例 6~7
 *
 * @see TombstoneMessage
 */
@DisplayName("TombstoneMessage — 墓碑消息单元测试")
class TombstoneMessageTest {

    // === 用例 6: emitTombstone 发送消息 ===
    @Test
    @DisplayName("emitTombstone: 正常构造，JSON 包含 messageIds、reason、ts")
    void tombstoneMessageConstructsCorrectly() {
        List<String> ids = Arrays.asList("msg-1", "msg-2", "msg-3");
        String reason = "test";

        TombstoneMessage tombstone = new TombstoneMessage(ids, reason);

        assertNotNull(tombstone);
        assertNotNull(tombstone.getId());
        assertEquals(3, tombstone.getMessageIds().size());
        assertTrue(tombstone.getMessageIds().containsAll(ids));
        assertEquals("test", tombstone.getReason());
        assertTrue(tombstone.getTimestamp() > 0);
    }

    @Test
    @DisplayName("toJson: 生成的 JSON 包含所有必要字段")
    void toJsonContainsAllFields() {
        List<String> ids = Arrays.asList("msg-a", "msg-b");
        TombstoneMessage tombstone = new TombstoneMessage(ids, "model switch");

        String json = tombstone.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"messageIds\":["));
        assertTrue(json.contains("\"msg-a\""));
        assertTrue(json.contains("\"msg-b\""));
        assertTrue(json.contains("\"reason\":\"model switch\""));
        assertTrue(json.contains("\"ts\":"));
    }

    @Test
    @DisplayName("toJson: messageIds 列表可序列化多元素 JSON 数组")
    void toJsonMultipleIds() {
        List<String> ids = Arrays.asList("id1", "id2", "id3", "id4", "id5");
        TombstoneMessage tombstone = new TombstoneMessage(ids, "compaction");

        String json = tombstone.toJson();

        for (String id : ids) {
            assertTrue(json.contains("\"" + id + "\""),
                "JSON 应包含消息ID: " + id);
        }
    }

    @Test
    @DisplayName("toJson: 特殊字符正确转义")
    void toJsonEscapesSpecialCharacters() {
        List<String> ids = Arrays.asList("msg-\"special\"");
        TombstoneMessage tombstone = new TombstoneMessage(ids, "reason\nwith\tnewline");

        String json = tombstone.toJson();

        assertTrue(json.contains("\\\"special\\\""), "双引号应被转义");
        assertTrue(json.contains("\\n"), "换行符应被转义为 \\n");
        assertTrue(json.contains("\\t"), "制表符应被转义为 \\t");
    }

    // === 用例 7: 空 ID 列表跳过 ===
    @Test
    @DisplayName("空 ID 列表: messageIds=[] 时构造正常但 JSON 中数组为空")
    void emptyIdListProducesEmptyJsonArray() {
        List<String> emptyIds = Collections.emptyList();
        TombstoneMessage tombstone = new TombstoneMessage(emptyIds, "no messages");

        assertNotNull(tombstone);
        assertTrue(tombstone.getMessageIds().isEmpty());

        String json = tombstone.toJson();
        assertTrue(json.contains("\"messageIds\":[]"),
            "空ID列表应产生空JSON数组");
    }

    @Test
    @DisplayName("messageIds 不可变: 返回的列表无法修改")
    void messageIdsIsUnmodifiable() {
        List<String> ids = Arrays.asList("id1");
        TombstoneMessage tombstone = new TombstoneMessage(ids, "test");

        assertThrows(UnsupportedOperationException.class,
            () -> tombstone.getMessageIds().add("id2"));
    }
}

