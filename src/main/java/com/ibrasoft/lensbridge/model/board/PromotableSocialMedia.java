package com.ibrasoft.lensbridge.model.board;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Represents a social media platform that can be promoted to a board
 * Includes platforms like Instagram pages, TikTok accounts, YouTube channels, and
 * WhatsApp chats
 */
@Entity
@Table(name = "promotable_social_media")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotableSocialMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /**
     * Duration of frame, in seconds
     */
    @Column(nullable = false)
    private int duration;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Audience audience;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SocialType type;

    @Column(nullable = false)
    private String url;

    /* All fields below will be stored as markdown in the database and serialized to HTML in the frontend
    That way, users can add italics for things like "your home on campus"
     */

    /**
     * Small 2-3 word tagline (i.e: follow along)
     */
    @Column(nullable = false)
    private String headerText;

    /**
     * Main text shown to the user
     */
    @Column(nullable = false)
    private String heroText;

    /**
     * Social media handle, if applicable (i.e: WhatsApp doesn't have a handle)
     */
    @Column(nullable = true)
    private String handle;

    /**
     * Descriptive block of text
     * i.e: Follow your home on campus for event recaps, announcements, and more!
     */
    @Column(nullable = false)
    private String footerText;

}
