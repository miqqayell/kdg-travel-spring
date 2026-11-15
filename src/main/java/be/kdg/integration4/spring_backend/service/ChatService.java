package be.kdg.integration4.spring_backend.service;

import be.kdg.integration4.spring_backend.controller.api.dto.chat.ChatMessageResponse;
import be.kdg.integration4.spring_backend.domain.*;
import be.kdg.integration4.spring_backend.domain.enums.ChatType;
import be.kdg.integration4.spring_backend.exception.BadRequestException;
import be.kdg.integration4.spring_backend.exception.ResourceNotFoundException;
import be.kdg.integration4.spring_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import be.kdg.integration4.spring_backend.repository.ChatRepository;
import be.kdg.integration4.spring_backend.repository.MessageRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final UserRepository userRepository;
    private final TravelGroupRepository travelGroupRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final TravelGroupService travelGroupService;

    @Transactional
    public ChatMessageResponse saveAndReturnMessage(Long groupId, String content, String originalUserId) {
        User sender = userRepository.findByOriginalId(originalUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        TravelGroup group = travelGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found"));

        // Security check
        if (!travelGroupService.isGroupCreator(groupId, originalUserId) && !travelGroupService.isUserMember(groupId, originalUserId)) {
            throw new BadRequestException("Not a member of this group");
        }

        Chat chat = group.getChat();
        if (chat == null) {
            chat = new Chat();
            chat.setChatType(ChatType.GROUP);
            chat.setCreatedAt(LocalDateTime.now());
            chat = chatRepository.save(chat);
            group.setChat(chat);
            travelGroupRepository.save(group);
        }

        Message message = new Message();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent(content);
        message.setTime(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        return new ChatMessageResponse(saved.getId(), sender.getId(), saved.getContent(), sender.getUsername(), sender.getProfilePictureUrl(), saved.getTime(), false);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Long groupId, String originalUserId) {
        TravelGroup group = travelGroupRepository.findById(groupId).orElseThrow();
        User currentUser = userRepository.findByOriginalId(originalUserId).orElseThrow();

        if (group.getChat() == null) return List.of();

        return messageRepository.findByChatIdOrderByTimeAsc(group.getChat().getId())
                .stream()
                .map(m -> new ChatMessageResponse(
                        m.getId(), m.getSender().getId(), m.getContent(), m.getSender().getUsername(),
                        m.getSender().getProfilePictureUrl(), m.getTime(),
                        m.getSender().getId().equals(currentUser.getId())))
                .toList();
    }
}
