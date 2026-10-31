package io.carabo.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.carabo.app.security.TenantContext;
import io.carabo.app.service.TenantCredentialsService;
import io.carabo.core.entity.Tenant;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/tenants/{id}/credentials")
@Produces(MediaType.APPLICATION_JSON)
public class TenantCredentialsResource {
  @Inject EntityManager em;
  @Inject TenantContext context;
  @Inject TenantCredentialsService credentials;

  private Tenant target(UUID id, boolean lock) {
    Tenant tenant =
        lock
            ? em.find(Tenant.class, id, LockModeType.PESSIMISTIC_WRITE)
            : em.find(Tenant.class, id);
    if (tenant == null || !context.canManage(tenant))
      throw new NotFoundException("Empresa no encontrada");
    return tenant;
  }

  private Response response(Object value) {
    return Response.ok(value).header("Cache-Control", "no-store").build();
  }

  @GET
  public Response status(@PathParam("id") UUID id) {
    return response(credentials.status(target(id, false)));
  }

  @GET
  @Path("/template")
  @Produces(MediaType.TEXT_PLAIN)
  public Response template(@PathParam("id") UUID id) {
    target(id, false);
    return Response.ok(TenantCredentialsService.blankTemplate(), "text/plain; charset=UTF-8")
        .header("Content-Disposition", "attachment; filename=carabo-credenciales-vacias.txt")
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .build();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response save(@PathParam("id") UUID id, JsonNode input) {
    Tenant tenant = target(id, true);
    try {
      if (input == null || !input.isObject() || input.size() > 6)
        throw new IllegalArgumentException("Formato de credenciales inválido");
      var values = new LinkedHashMap<String, String>();
      var fields = input.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        if (!field.getValue().isTextual() && !field.getValue().isNull())
          throw new IllegalArgumentException("Los campos de credenciales deben contener texto");
        values.put(field.getKey(), field.getValue().isNull() ? null : field.getValue().textValue());
      }
      return response(credentials.update(tenant, values, actor()));
    } catch (IllegalArgumentException error) {
      throw new BadRequestException(
          Response.status(400).entity(Map.of("error", error.getMessage())).build());
    } catch (IllegalStateException error) {
      throw new ServiceUnavailableException(
          Response.status(503)
              .entity(Map.of("error", "El almacén seguro de credenciales no está disponible"))
              .build());
    }
  }

  @POST
  @Path("/import")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Transactional
  public Response importFile(@PathParam("id") UUID id, @RestForm("credentials") FileUpload upload) {
    try {
      Tenant tenant = target(id, true);
      if (upload == null || upload.size() <= 0 || upload.size() > 16384)
        throw new IllegalArgumentException("Selecciona un archivo TXT de hasta 16 KB");
      byte[] bytes;
      try (var input = Files.newInputStream(upload.uploadedFile())) {
        bytes = input.readNBytes(16385);
      }
      if (bytes.length > 16384)
        throw new IllegalArgumentException("Selecciona un archivo TXT de hasta 16 KB");
      String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
      return response(credentials.update(tenant, TenantCredentialsService.parse(text), actor()));
    } catch (IllegalArgumentException error) {
      throw new BadRequestException(
          Response.status(400).entity(Map.of("error", error.getMessage())).build());
    } catch (java.nio.charset.CharacterCodingException error) {
      throw new BadRequestException(
          Response.status(400)
              .entity(Map.of("error", "El TXT debe estar codificado en UTF-8"))
              .build());
    } catch (java.io.IOException | IllegalStateException error) {
      throw new ServiceUnavailableException(
          Response.status(503)
              .entity(Map.of("error", "No se pudo guardar el archivo en el almacén seguro"))
              .build());
    } finally {
      if (upload != null)
        try {
          Files.deleteIfExists(upload.uploadedFile());
        } catch (java.io.IOException ignored) {
        }
    }
  }

  private String actor() {
    return context.userCode() != null ? context.userCode() : "platform-administrator";
  }
}
