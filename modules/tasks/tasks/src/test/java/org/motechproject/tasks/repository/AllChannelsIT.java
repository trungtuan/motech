package org.motechproject.tasks.repository;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.ektorp.CouchDbConnector;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.commons.api.json.MotechJsonReader;
import org.motechproject.tasks.domain.Channel;
import org.motechproject.tasks.json.ActionEventRequestDeserializer;
import org.motechproject.tasks.contract.ActionEventRequest;
import org.motechproject.tasks.contract.ChannelRequest;
import org.motechproject.testing.utils.SpringIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath*:/META-INF/motech/*.xml"})
public class AllChannelsIT extends SpringIntegrationTest {

    @Autowired
    private AllChannels allChannels;

    @Autowired
    @Qualifier("taskDbConnector")
    private CouchDbConnector couchDbConnector;

    private MotechJsonReader motechJsonReader = new MotechJsonReader();

    @Ignore
    @Test
    public void shouldAddAndUpdateChannels() throws IOException {
        List<Channel> channels = loadChannels();

        assertFalse(allChannels.addOrUpdate(channels.get(0)));
        assertFalse(allChannels.addOrUpdate(channels.get(1)));
        assertEquals(channels, allChannels.getAll());

        assertTrue(allChannels.addOrUpdate(channels.get(1)));
        assertEquals(channels, allChannels.getAll());

        markForDeletion(allChannels.getAll());
    }

    @Ignore
    @Test
    public void shouldFindChannelByChannelInfo() throws Exception {
        List<Channel> channels = loadChannels();

        assertFalse(allChannels.addOrUpdate(channels.get(0)));
        assertFalse(allChannels.addOrUpdate(channels.get(1)));

        List<Channel> channelList = allChannels.getAll();

        assertEquals(channels, channelList);

        Channel channel = channelList.get(0);
        Channel actual = allChannels.byModuleName(channel.getModuleName());

        assertEquals(channel, actual);

        channel = channelList.get(1);
        actual = allChannels.byModuleName(channel.getModuleName());

        assertEquals(channel, actual);

        markForDeletion(allChannels.getAll());
    }

    private List<Channel> loadChannels() throws IOException {
        Type type = new TypeToken<ChannelRequest>() {
        }.getType();

        HashMap<Type, Object> typeAdapters = new HashMap<>();
        typeAdapters.put(ActionEventRequest.class, new ActionEventRequestDeserializer());

        List<StringWriter> writers = new ArrayList<>(2);

        for (String json : Arrays.asList("/message-campaign-test-channel.json", "/pillreminder-test-channel.json")) {
            try (InputStream stream = getClass().getResourceAsStream(json)) {
                StringWriter writer = new StringWriter();
                IOUtils.copy(stream, writer);

                writers.add(writer);
            }
        }

        List<Channel> channelRequests = new ArrayList<>(2);

        for (StringWriter writer : writers) {
            ChannelRequest channelRequest = (ChannelRequest) motechJsonReader.readFromString(writer.toString(), type, typeAdapters);
            channelRequest.setModuleName(channelRequest.getDisplayName());
            channelRequest.setModuleVersion("1.0");
            channelRequests.add(new Channel(channelRequest));
        }

        return channelRequests;
    }

    @Override
    public CouchDbConnector getDBConnector() {
        return couchDbConnector;
    }
}
