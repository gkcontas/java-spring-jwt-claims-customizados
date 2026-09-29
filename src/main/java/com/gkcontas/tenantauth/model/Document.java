package com.gkcontas.tenantauth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tenant and owner are plain ids rather than associations, deliberately.
 *
 * <p>An authorization check that has to load the owning {@code User} to compare a tenant
 * runs an extra query on every request, and a lazy association makes it easy to leak a
 * whole object graph into a response. The two columns the rules compare are kept flat.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "required_clearance", nullable = false)
    private Integer requiredClearance;

    /** Null means "any department inside the tenant may read it". */
    @Column(length = 60)
    private String department;

    public Document(Long tenantId, Long ownerId, String title, String content,
                    Integer requiredClearance, String department) {
        this.tenantId = tenantId;
        this.ownerId = ownerId;
        this.title = title;
        this.content = content;
        this.requiredClearance = requiredClearance;
        this.department = department;
    }
}
