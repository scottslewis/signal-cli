package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.OutputType;
import org.asamk.signal.Shutdown;
import org.asamk.signal.commands.exceptions.CommandException;
import org.asamk.signal.jsonrpc.SignalJsonRpcDispatcherHandler;
import org.asamk.signal.manager.Manager;
import org.asamk.signal.manager.MultiAccountManager;
import org.asamk.signal.output.JsonWriter;
import org.asamk.signal.output.OutputWriter;
import org.asamk.signal.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.util.List;
import java.util.function.Supplier;

import static org.asamk.signal.util.CommandUtil.getReceiveConfig;

public class JsonRpcDispatcherCommand implements LocalCommand, MultiLocalCommand {

    private static final Logger logger = LoggerFactory.getLogger(JsonRpcDispatcherCommand.class);

    @Override
    public String getName() {
        return "jsonRpc";
    }

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.help("Take commands from standard input as line-delimited JSON RPC while receiving messages.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--ignore-stories")
                .help("Don’t receive story messages from the server.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--send-read-receipts")
                .help("Send read receipts for all incoming data messages (in addition to the default delivery receipts)")
                .action(Arguments.storeTrue());
        subparser.addArgument("--receive-mode")
                .help("Specify when to start receiving messages.")
                .type(Arguments.enumStringType(ReceiveMode.class))
                .setDefault(ReceiveMode.ON_START);
    }

    @Override
    public List<OutputType> getSupportedOutputTypes() {
        return List.of(OutputType.JSON);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final Manager m,
            final OutputWriter outputWriter
    ) throws CommandException {
        Shutdown.installHandler();
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var receiveConfig = getReceiveConfig(ns);
        m.setReceiveConfig(receiveConfig);

        final var jsonOutputWriter = (JsonWriter) outputWriter;
        final var lineSupplier = getLineSupplier();

        final var handler = new SignalJsonRpcDispatcherHandler(jsonOutputWriter,
                lineSupplier,
                receiveMode == ReceiveMode.MANUAL);
        final var thread = Thread.currentThread();
        Shutdown.registerShutdownListener(thread::interrupt);
        handler.handleConnection(m);
    }

    @Override
    public void handleCommand(
            final Namespace ns,
            final MultiAccountManager c,
            final OutputWriter outputWriter
    ) throws CommandException {
        Shutdown.installHandler();
        final var receiveMode = ns.<ReceiveMode>get("receive-mode");
        final var receiveConfig = getReceiveConfig(ns);
        c.getManagers().forEach(m -> m.setReceiveConfig(receiveConfig));
        c.addOnManagerAddedHandler(m -> m.setReceiveConfig(receiveConfig));

        final var jsonOutputWriter = (JsonWriter) outputWriter;
        final var lineSupplier = getLineSupplier();

        final var handler = new SignalJsonRpcDispatcherHandler(jsonOutputWriter,
                lineSupplier,
                receiveMode == ReceiveMode.MANUAL);
        final var thread = Thread.currentThread();
        Shutdown.registerShutdownListener(thread::interrupt);
        handler.handleConnection(c);
    }

    private static Supplier<String> getLineSupplier() {
        // Use FileChannel for stdin, because System.in is uninterruptible
        final var stdInCh = Channels.newInputStream((new FileInputStream(FileDescriptor.in)).getChannel());
        return IOUtils.getLineSupplier(new InputStreamReader(stdInCh, IOUtils.getConsoleCharset()));
    }
}
