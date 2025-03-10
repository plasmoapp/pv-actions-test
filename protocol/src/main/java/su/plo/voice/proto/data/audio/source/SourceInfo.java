package su.plo.voice.proto.data.audio.source;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import su.plo.voice.proto.packets.PacketSerializable;
import su.plo.voice.proto.packets.PacketUtil;

import java.io.IOException;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

@AllArgsConstructor
@NoArgsConstructor
@ToString
public abstract class SourceInfo implements PacketSerializable {

    public static SourceInfo of(ByteArrayDataInput in) throws IOException {
        SourceInfo sourceInfo = null;
        switch (Type.valueOf(in.readUTF())) {
            case PLAYER:
                sourceInfo = new PlayerSourceInfo();
                break;
            case ENTITY:
                sourceInfo = new EntitySourceInfo();
                break;
            case STATIC:
                sourceInfo = new StaticSourceInfo();
                break;
            case DIRECT:
                sourceInfo = new DirectSourceInfo();
                break;
        }

        if (sourceInfo == null) throw new IllegalArgumentException("Invalid source type");

        sourceInfo.deserialize(in);
        return sourceInfo;
    }

    @Getter
    protected String addonId;
    @Getter
    protected UUID id;
    @Getter
    protected UUID lineId;
    @Getter
    protected byte state;
    @Getter
    protected String codec;
    @Getter
    protected boolean stereo;
    @Getter
    protected boolean iconVisible;
    @Getter
    protected int angle;

    @Override
    public void deserialize(ByteArrayDataInput in) throws IOException {
        this.addonId = in.readUTF();
        this.id = PacketUtil.readUUID(in);
        this.state = in.readByte();
        this.codec = PacketUtil.readNullableString(in);
        this.stereo = in.readBoolean();
        this.lineId = PacketUtil.readUUID(in);
        this.iconVisible = in.readBoolean();
        this.angle = in.readInt();
    }

    @Override
    public void serialize(ByteArrayDataOutput out) throws IOException {
        out.writeUTF(getType().name());
        out.writeUTF(checkNotNull(addonId));
        PacketUtil.writeUUID(out, checkNotNull(id));
        out.writeByte(state);
        PacketUtil.writeNullableString(out, codec);
        out.writeBoolean(stereo);
        PacketUtil.writeUUID(out, lineId);
        out.writeBoolean(iconVisible);
        out.writeInt(angle);
    }

    public abstract Type getType();

    public enum Type {
        PLAYER,
        ENTITY,
        STATIC,
        DIRECT
    }
}
