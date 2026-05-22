package com.serverscout.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 256)
    private String password;

    @Column(length = 64)
    private String name;

    @Column(length = 16)
    private String gender;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, length = 128)
    private String email;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.role == null) this.role = "USER";
        if (this.enabled == null) this.enabled = true;
        this.createdAt = Instant.now();
    }
}
