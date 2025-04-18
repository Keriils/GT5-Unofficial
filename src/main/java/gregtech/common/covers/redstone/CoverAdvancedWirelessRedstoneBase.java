package gregtech.common.covers.redstone;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.google.common.io.ByteArrayDataInput;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import gregtech.api.GregTechAPI;
import gregtech.api.covers.CoverContext;
import gregtech.api.gui.modularui.CoverUIBuildContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.util.GTUtility;
import gregtech.api.util.ISerializableObject;
import gregtech.common.covers.CoverBehaviorBase;
import gregtech.common.gui.modularui.widget.CoverDataControllerWidget;
import gregtech.common.gui.modularui.widget.CoverDataFollowerNumericWidget;
import gregtech.common.gui.modularui.widget.CoverDataFollowerToggleButtonWidget;
import io.netty.buffer.ByteBuf;

public abstract class CoverAdvancedWirelessRedstoneBase<T extends CoverAdvancedWirelessRedstoneBase.WirelessData>
    extends CoverBehaviorBase<T> {

    public CoverAdvancedWirelessRedstoneBase(CoverContext context, Class<T> typeToken, ITexture coverTexture) {
        super(context, typeToken, coverTexture);
    }

    public static Byte getSignalAt(UUID uuid, int frequency, CoverAdvancedRedstoneReceiverBase.GateMode mode) {
        Map<Integer, Map<Long, Byte>> frequencies = GregTechAPI.sAdvancedWirelessRedstone.get(String.valueOf(uuid));
        if (frequencies == null) return 0;

        Map<Long, Byte> signals = frequencies.get(frequency);
        if (signals == null) signals = new ConcurrentHashMap<>();

        switch (mode) {
            case AND -> {
                return (byte) (signals.values()
                    .stream()
                    .map(signal -> signal > 0)
                    .reduce(true, (signalA, signalB) -> signalA && signalB) ? 15 : 0);
            }
            case NAND -> {
                return (byte) (signals.values()
                    .stream()
                    .map(signal -> signal > 0)
                    .reduce(true, (signalA, signalB) -> signalA && signalB) ? 0 : 15);
            }
            case OR -> {
                return (byte) (signals.values()
                    .stream()
                    .map(signal -> signal > 0)
                    .reduce(false, (signalA, signalB) -> signalA || signalB) ? 15 : 0);
            }
            case NOR -> {
                return (byte) (signals.values()
                    .stream()
                    .map(signal -> signal > 0)
                    .reduce(false, (signalA, signalB) -> signalA || signalB) ? 0 : 15);
            }
            case SINGLE_SOURCE -> {
                if (signals.values()
                    .isEmpty()) {
                    return 0;
                }
                return signals.values()
                    .iterator()
                    .next();
            }
            default -> {
                return 0;
            }
        }
    }

    public static void removeSignalAt(UUID uuid, int frequency, long hash) {
        Map<Integer, Map<Long, Byte>> frequencies = GregTechAPI.sAdvancedWirelessRedstone.get(String.valueOf(uuid));
        if (frequencies == null) return;
        frequencies.computeIfPresent(frequency, (freq, longByteMap) -> {
            longByteMap.remove(hash);
            return longByteMap.isEmpty() ? null : longByteMap;
        });
    }

    public static void setSignalAt(UUID uuid, int frequency, long hash, byte value) {
        Map<Integer, Map<Long, Byte>> frequencies = GregTechAPI.sAdvancedWirelessRedstone
            .computeIfAbsent(String.valueOf(uuid), k -> new ConcurrentHashMap<>());
        Map<Long, Byte> signals = frequencies.computeIfAbsent(frequency, k -> new ConcurrentHashMap<>());
        signals.put(hash, value);
    }

    /**
     * x hashed into first 20 bytes y hashed into second 20 bytes z hashed into fifth 10 bytes dim hashed into sixth 10
     * bytes side hashed into last 4 bytes
     */
    public static long hashCoverCoords(ICoverable tile, ForgeDirection side) {
        return (((((long) tile.getXCoord() << 20) + tile.getZCoord() << 10) + tile.getYCoord() << 10)
            + tile.getWorld().provider.dimensionId << 4) + side.ordinal();
    }

    @Override
    public boolean letsEnergyIn() {
        return true;
    }

    @Override
    public boolean letsEnergyOut() {
        return true;
    }

    @Override
    public boolean letsFluidIn(Fluid aFluid) {
        return true;
    }

    @Override
    public boolean letsFluidOut(Fluid aFluid) {
        return true;
    }

    @Override
    public boolean letsItemsIn(int aSlot) {
        return true;
    }

    @Override
    public boolean letsItemsOut(int aSlot) {
        return true;
    }

    @Override
    public String getDescription() {
        return GTUtility.trans("081", "Frequency: ") + coverData.frequency
            + ", Transmission: "
            + (coverData.uuid == null ? "Public" : "Private");
    }

    @Override
    public int getMinimumTickRate() {
        return 1;
    }

    @Override
    public int getDefaultTickRate() {
        return 5;
    }

    public abstract static class WirelessData implements ISerializableObject {

        protected int frequency;

        /**
         * If UUID is set to null, the cover frequency is public, rather than private
         **/
        protected UUID uuid;

        public WirelessData(int frequency, UUID uuid) {
            this.frequency = frequency;
            this.uuid = uuid;
        }

        public UUID getUuid() {
            return uuid;
        }

        public int getFrequency() {
            return frequency;
        }

        @Nonnull
        @Override
        public NBTBase saveDataToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("frequency", frequency);
            if (uuid != null) {
                tag.setString("uuid", uuid.toString());
            }

            return tag;
        }

        @Override
        public void writeToByteBuf(ByteBuf aBuf) {
            aBuf.writeInt(frequency);
            aBuf.writeBoolean(uuid != null);
            if (uuid != null) {
                aBuf.writeLong(uuid.getLeastSignificantBits());
                aBuf.writeLong(uuid.getMostSignificantBits());
            }
        }

        @Override
        public void loadDataFromNBT(NBTBase aNBT) {
            NBTTagCompound tag = (NBTTagCompound) aNBT;
            frequency = tag.getInteger("frequency");
            if (tag.hasKey("uuid")) {
                uuid = UUID.fromString(tag.getString("uuid"));
            }
        }

        @Override
        public void readFromPacket(ByteArrayDataInput aBuf) {
            frequency = aBuf.readInt();
            if (aBuf.readBoolean()) {
                uuid = new UUID(aBuf.readLong(), aBuf.readLong());
            }
        }
    }

    // GUI stuff

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    protected abstract class AdvancedWirelessRedstoneBaseUIFactory extends UIFactory {

        protected static final int startX = 10;
        protected static final int startY = 25;
        protected static final int spaceX = 18;
        protected static final int spaceY = 18;

        public AdvancedWirelessRedstoneBaseUIFactory(CoverUIBuildContext buildContext) {
            super(buildContext);
        }

        @Override
        protected int getGUIWidth() {
            return 250;
        }

        @Override
        protected void addUIWidgets(ModularWindow.Builder builder) {
            final int privateExtraColumn = isShiftPrivateLeft() ? 1 : 5;

            CoverDataControllerWidget<T> dataController = new CoverDataControllerWidget<>(
                this::getCoverData,
                this::setCoverData,
                CoverAdvancedWirelessRedstoneBase.this::loadFromNbt);
            dataController.setPos(startX, startY);
            addUIForDataController(dataController);

            builder.widget(dataController)
                .widget(
                    new TextWidget(GTUtility.trans("246", "Frequency")).setDefaultColor(COLOR_TEXT_GRAY.get())
                        .setPos(startX + spaceX * 5, 4 + startY + spaceY * getFrequencyRow()))
                .widget(
                    new TextWidget(GTUtility.trans("602", "Use Private Frequency"))
                        .setDefaultColor(COLOR_TEXT_GRAY.get())
                        .setPos(startX + spaceX * privateExtraColumn, 4 + startY + spaceY * getButtonRow()));
        }

        protected void addUIForDataController(CoverDataControllerWidget<T> controller) {
            controller.addFollower(
                new CoverDataFollowerNumericWidget<>(),
                coverData -> (double) coverData.frequency,
                (coverData, state) -> {
                    coverData.frequency = state.intValue();
                    return coverData;
                },
                widget -> widget.setScrollValues(1, 1000, 10)
                    .setBounds(0, Integer.MAX_VALUE)
                    .setPos(1, 2 + spaceY * getFrequencyRow())
                    .setSize(spaceX * 5 - 4, 12))
                .addFollower(
                    CoverDataFollowerToggleButtonWidget.ofCheck(),
                    coverData -> coverData.uuid != null,
                    (coverData, state) -> {
                        if (state) {
                            coverData.uuid = getUIBuildContext().getPlayer()
                                .getUniqueID();
                        } else {
                            coverData.uuid = null;
                        }
                        return coverData;
                    },
                    widget -> widget.setPos(0, spaceY * getButtonRow()));
        }

        protected abstract int getFrequencyRow();

        protected abstract int getButtonRow();

        protected abstract boolean isShiftPrivateLeft();
    }
}
