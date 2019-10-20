package org.builder.session.jackson.client.messages;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskMetadata {

    @SerializedName("Cluster")
    private final String cluster;
    @SerializedName("TaskARN")
    private final String taskARN;
    @SerializedName("Family")
    private final String family;
    @SerializedName("Revision")
    private final String revision;
    @SerializedName("DesiredStatus")
    private final String desiredStatus;
    @SerializedName("KnownStatus")
    private final String knownStatus;
    @SerializedName("Containers")
    private final List<ContainerMetadata> containers;
    @SerializedName("Limits")
    private final Map<String, Long> limits;
    @SerializedName("PullStartedAt")
    private final ZonedDateTime pullStartedAt;
    @SerializedName("PullStoppedAt")
    private final ZonedDateTime pullStoppedAt;
    @SerializedName("AvailabilityZone")
    private final String availabilityZone;
}
