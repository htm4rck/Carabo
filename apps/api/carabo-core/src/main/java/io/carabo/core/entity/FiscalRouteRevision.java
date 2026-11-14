package io.carabo.core.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="fiscal_route_revision",schema="carabo",uniqueConstraints=@UniqueConstraint(columnNames={"business_code","version"}))
public class FiscalRouteRevision {
    @Id public UUID id;
    @Column(name="business_code",nullable=false) public String businessCode;
    @Column(nullable=false) public int version;
    @Column(name="routes_json",nullable=false,columnDefinition="text") public String routesJson;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="actor_code",nullable=false) public String actorCode;
}
