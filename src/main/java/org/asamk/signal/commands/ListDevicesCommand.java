package org.asamk.signal.commands;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.commands.exceptions.IOErrorException;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.api.Device;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.output.PlainTextWriter;
import org.asamk.signal.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ListDevicesCommand implements JsonRpcLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(ListDevicesCommand.class);

    @Override
    public String getName() {
        return "listDevices";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Show a list of linked devices.");
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        List<Device> devices;
        try {
            devices = m.getLinkedDevices();
        } catch (IOException e) {
            throw new IOErrorException("Failed to get linked devices: " + e.getMessage(), e);
        }

        switch (outputWriter) {
            case PlainTextWriter writer -> {
                for (var d : devices) {
                    writer.println("- Device {}{}:", d.id(), (d.isThisDevice() ? " (this device)" : ""));
                    writer.indent(w -> {
                        w.println("Name: {}", d.name());
                        w.println("Created: {}", DateUtils.formatTimestamp(d.created()));
                        w.println("Last seen: {}", DateUtils.formatTimestamp(d.lastSeen()));
                    });
                }
            }
            case JsonWriter writer -> {
                final var jsonDevices = devices.stream()
                        .map(d -> new JsonDevice(d.id(), d.name(), d.created(), d.lastSeen()))
                        .toList();
                writer.write(jsonDevices);
            }
        }
    }

    private record JsonDevice(long id, String name, long createdTimestamp, long lastSeenTimestamp) {}
}
