package bg.pu.hla.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(length = 8000, nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    private ChatMode modeUsed;

    @Column(length = 2000)
    private String ontologySources;

    @Column(length = 4000)
    private String sparqlContext;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ChatRole {
        USER,
        ASSISTANT
    }
}
