package org.motechproject.tasks.service;

import org.motechproject.server.api.BundleIcon;
import org.motechproject.tasks.contract.ChannelRequest;
import org.motechproject.tasks.domain.Channel;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Manages CRUD operations for a {@link Channel}.
 */
public interface ChannelService {

    void registerChannel(ChannelRequest channelRequest);

    void registerChannel(InputStream stream, String moduleName, String moduleVersion);

    void addOrUpdate(final Channel channel);

    List<Channel> getAllChannels();

    Channel getChannel(String moduleName);

    BundleIcon getChannelIcon(String moduleName) throws IOException;
}
