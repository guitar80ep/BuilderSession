package org.builder.session.jackson.client.messages;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContainerMetadata {
    @SerializedName("DockerId")
    private final String dockerId;
    @SerializedName("Name")
    private final String name;
    @SerializedName("DockerName")
    private final String dockerName;
    @SerializedName("Image")
    private final String image;
    @SerializedName("ImageId")
    private final String imageId;
    @SerializedName("Labels")
    private final Map<String, String> labels;
    @SerializedName("DesiredStatus")
    private final String desiredStatus;
    @SerializedName("KnownStatus")
    private final String knownStatus;
    @SerializedName("Limits")
    private final Map<String, Long> limits;
    @SerializedName("CreatedAt")
    private final Timestamp createdAt;
    @SerializedName("StartedAt")
    private final Timestamp startedAt;
    @SerializedName("Type")
    private final String type;
    @SerializedName("Networks")
    private final List<Network> networks;

    @Data
    @Builder
    public class Network {
        @SerializedName("NetworkMode")
        private final String networkMode;
        @SerializedName("IPv4Addresses")
        private final List<String> IPv4Addresses;
    }
}
