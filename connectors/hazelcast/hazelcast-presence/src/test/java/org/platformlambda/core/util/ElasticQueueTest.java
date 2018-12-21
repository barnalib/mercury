package org.platformlambda.core.util;

import org.junit.Test;
import org.platformlambda.core.models.EventEnvelope;
import org.platformlambda.core.serializers.PayloadMapper;
import org.platformlambda.core.util.models.PoJo;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ElasticQueueTest {

    @Test
    public void normalPayload() throws IOException {
        readWrite("normal", 10);
    }

    @Test
    public void largePayload() throws IOException {
        readWrite("large", 90000);
    }

    private void readWrite(String path, int size) throws IOException {
        String target = "hello.world";
        // create input
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < size; i++) {
            sb.append("0123456789");
        }
        sb.append(": ");
        String baseText = sb.toString();
        // temp queue directory
        File dir = new File("fifo");
        ElasticQueue spooler = new ElasticQueue(dir, path);
        // immediate read after write
        for (int i = 0; i < ElasticQueue.MEMORY_BUFFER * 3; i++) {
            String input = baseText + i;
            EventEnvelope event = new EventEnvelope();
            event.setTo(target);
            event.setBody(input);
            spooler.write(event);
            EventEnvelope data = spooler.read();
            assertTrue(data != null);
            assertTrue(input.equals(data.getBody()));
        }
        /*
         * Test serialization of Java objects
         * by writing a larger number of messages to force buffering to disk
         */
        for (int i = 0; i < ElasticQueue.MEMORY_BUFFER * 3; i++) {
            String input = baseText+i;
            PoJo pojo = new PoJo();
            EventEnvelope event = new EventEnvelope();
            event.setTo(target);
            pojo.setName(input);
            event.setBody(pojo);
            spooler.write(event);
        }
        // then read the messages
        for (int i = 0; i < ElasticQueue.MEMORY_BUFFER * 3; i++) {
            String input = baseText+i;
            EventEnvelope data = spooler.read();
            assertTrue(data != null);
            assertTrue(data.getBody() instanceof PoJo);
            PoJo o = (PoJo) data.getBody();
            assertTrue(input.equals(o.getName()));
        }
        // it should return null when there are no more messages to be read
        EventEnvelope nothing = spooler.read();
        assertEquals(null, nothing);
        // elastic queue should be automatically closed when all messages are consumed
        assertTrue(spooler.isClosed());
        // closing again has no effect
        spooler.close();
        // remove elastic queue folder
        spooler.destroy();
        // remove temp directory
        if (dir.exists()) {
            Utility.getInstance().cleanupDir(dir);
        }
        // finally, verify if the PoJo class name is cached
        ManagedCache cache = ManagedCache.getInstance(PayloadMapper.JAVA_CLASS_CACHE);
        assertTrue(cache != null);
        assertTrue(cache.exists(PoJo.class.getName()));
    }

}