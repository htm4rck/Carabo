package io.carabo.app.rest;

import io.carabo.app.client.LayoutClient;
import io.carabo.app.security.TenantContext;
import io.carabo.app.service.DispatchGuideService;
import io.carabo.core.entity.Tenant;
import io.carabo.core.entity.gre.DispatchGuide;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DispatchGuidePrintTest {
    @Test void blocksThePdfAtTheApiBeforeCallingTheRendererAndPreservesTenantIsolation(){
        var calls=new AtomicInteger();var guide=new DispatchGuide();guide.setId(UUID.randomUUID());guide.setBusinessCode("B20123456789");
        var resource=new DispatchGuideResource();var tenant=new Tenant();tenant.setBusinessCode(guide.getBusinessCode());
        resource.tenantContext=new TenantContext();resource.tenantContext.authenticateUser(tenant,"U1",Set.of("ADMIN_Carabo"),true);
        resource.service=new DispatchGuideService(){
            @Override public Optional<DispatchGuide> findById(UUID id,String business){return guide.getId().equals(id)&&guide.getBusinessCode().equals(business)?Optional.of(guide):Optional.empty();}
            @Override public boolean printable(DispatchGuide value){return false;}
        };
        resource.layoutClient=new LayoutClient(){@Override public byte[] generatePdf(DispatchGuide value,String format,String authorization){calls.incrementAndGet();return new byte[0];}};
        var denied=resource.getPdf(guide.getId(),"Bearer session");assertEquals(409,denied.getStatus());assertEquals(0,calls.get());
        assertEquals("GRE_NOT_TRANSPORT_AUTHORIZED",((DispatchGuideResource.ErrorResponse)denied.getEntity()).code());
        tenant.setBusinessCode("B20999999999");assertEquals(404,resource.getPdf(guide.getId(),"Bearer session").getStatus());assertEquals(0,calls.get());
    }
}
