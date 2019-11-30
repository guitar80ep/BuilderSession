package org.builder.session.jackson.health;

import static org.builder.session.jackson.utils.CommandLineArguments.parseArg;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.build.session.jackson.proto.Candidate;
import org.build.session.jackson.proto.ConsumeRequest;
import org.build.session.jackson.proto.ConsumeResponse;
import org.builder.session.jackson.client.consumer.ConsumerBackendClient;

public class Healthcheck {

    public static void main(String[] args) throws UnknownHostException {

        int port = parseArg(args,
                            true,
                            "--port",
                            s -> Integer.parseInt(s)).get();


        try {
            String address = InetAddress.getLocalHost().getHostAddress();
            try(ConsumerBackendClient client = new ConsumerBackendClient(address, port)) {
                ConsumeResponse response = client.call(ConsumeRequest.newBuilder()
                                                                     .setCandidate(Candidate.ALL)
                                                                     .build());
                if(response.getErrorList().size() <= 0) {
                    System.out.println("Succeeded healthcheck with response: " + response);
                    System.exit(0);
                } else {
                    System.err.println("Failed healthcheck with response: " + response);
                    System.exit(1);
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed healthcheck due to: " + t);
            System.exit(1);
        }


    }
}
