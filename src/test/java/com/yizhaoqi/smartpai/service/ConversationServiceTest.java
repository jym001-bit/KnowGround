package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.model.Conversation;
import com.yizhaoqi.smartpai.model.ConversationSession;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.repository.ConversationRepository;
import com.yizhaoqi.smartpai.repository.ConversationSessionRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ConversationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationSessionRepository sessionRepository;

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testRecordConversation() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        conversationService.recordConversation("testuser", "What is AI?", "AI stands for Artificial Intelligence.");

        verify(conversationRepository, times(1)).save(any(Conversation.class));
    }

    @Test
    void testGetConversations() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setQuestion("What is AI?");
        conversation.setAnswer("AI stands for Artificial Intelligence.");
        when(conversationRepository.findByUserIdOrderByTimestampAsc(anyLong())).thenReturn(List.of(conversation));

        var result = conversationService.getConversations("testuser", null, null);
        assertEquals(1, result.size());
    }

    @Test
    void anotherUserCannotReadOrSwitchConversationById() {
        User admin = new User();
        admin.setId(1L);
        ConversationSession session = new ConversationSession();
        session.setConversationId("admin-session");
        session.setUser(admin);
        when(sessionRepository.findByConversationId("admin-session")).thenReturn(Optional.of(session));

        assertThrows(CustomException.class,
                () -> conversationService.getMessagesByConversationId(2L, "admin-session"));
        assertThrows(CustomException.class,
                () -> conversationService.switchCurrentConversation(2L, "admin-session"));
        assertThrows(CustomException.class,
                () -> conversationService.archiveConversationSession(2L, "admin-session"));

        verify(conversationRepository, never()).findByConversationIdOrderByTimestampAsc(any());
        verify(sessionRepository, never()).save(any(ConversationSession.class));
    }

    @Test
    void ownerCanReadConversationById() {
        User owner = new User();
        owner.setId(2L);
        ConversationSession session = new ConversationSession();
        session.setConversationId("own-session");
        session.setUser(owner);
        when(sessionRepository.findByConversationId("own-session")).thenReturn(Optional.of(session));
        when(conversationRepository.findByConversationIdOrderByTimestampAsc("own-session"))
                .thenReturn(List.of());

        assertEquals(List.of(), conversationService.getMessagesByConversationId(2L, "own-session"));
    }

    @Test
    void historicalAdminReferenceIsNotAccessibleToAnotherUser() {
        FileUpload adminFile = new FileUpload();
        adminFile.setFileMd5("admin-md5");
        adminFile.setUserId("1");
        adminFile.setPublic(false);
        when(fileUploadRepository.findByFileMd5In(any())).thenReturn(List.of(adminFile));
        Map<String, Map<String, Object>> references = Map.of(
                "1", Map.of("fileMd5", "admin-md5"));

        assertFalse(conversationService.isReferenceMappingAccessible(2L, references));
        assertTrue(conversationService.isReferenceMappingAccessible(1L, references));
    }
}
