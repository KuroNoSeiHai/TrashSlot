package net.blay09.mods.trashslot.net;

import net.blay09.mods.trashslot.TrashHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageDeleteFromSlot {

    private final int slotNumber;
    private final boolean isShiftDown;

    public MessageDeleteFromSlot(int slotNumber, boolean isShiftDown) {
        this.slotNumber = slotNumber;
        this.isShiftDown = isShiftDown;
    }

    public static void encode(final MessageDeleteFromSlot message, final PacketBuffer buf) {
        buf.writeByte(message.slotNumber);
        buf.writeBoolean(message.isShiftDown);
    }

    public static MessageDeleteFromSlot decode(final PacketBuffer buf) {
        int slotNumber = buf.readByte();
        boolean isShiftDown = buf.readBoolean();
        return new MessageDeleteFromSlot(slotNumber, isShiftDown);
    }

    public static void handle(final MessageDeleteFromSlot message, final Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            EntityPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (message.slotNumber == -1) {
                TrashHelper.setTrashItem(player, ItemStack.EMPTY);
                return;
            }

            if (!player.inventory.getItemStack().isEmpty()) {
                return;
            }

            Container container = player.openContainer;
            Slot deleteSlot = container.inventorySlots.get(message.slotNumber);
            if (deleteSlot instanceof SlotCrafting) {
                return;
            }

            if (message.isShiftDown) {
                ItemStack deleteStack = deleteSlot.getStack().copy();
                if (!deleteStack.isEmpty()) {
                    if (attemptDeleteFromSlot(player, container, message.slotNumber)) {
                        for (int i = 0; i < container.inventorySlots.size(); i++) {
                            ItemStack slotStack = container.inventorySlots.get(i).getStack();
                            if (!slotStack.isEmpty()
                                    && ItemStack.areItemsEqualIgnoreDurability(slotStack, deleteStack)
                                    && ItemStack.areItemStackTagsEqual(slotStack, deleteStack)) {
                                if (!attemptDeleteFromSlot(player, container, i)) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } else {
                attemptDeleteFromSlot(player, container, message.slotNumber);
            }

            NetworkHandler.instance.reply(new MessageTrashSlotContent(TrashHelper.getTrashItem(player)), context);
        });
    }

    private static boolean attemptDeleteFromSlot(EntityPlayer player, Container container, int slotNumber) {
        ItemStack itemStack = container.slotClick(slotNumber, 0, ClickType.PICKUP, player);
        ItemStack mouseStack = player.inventory.getItemStack();
        if (ItemStack.areItemStacksEqual(itemStack, mouseStack)) {
            player.inventory.setItemStack(ItemStack.EMPTY);
            TrashHelper.setTrashItem(player, mouseStack);
            return !itemStack.isEmpty();
        } else {
            // Abort mission - something went weirdly wrong - sync the current mouse item to prevent desyncs
            ((EntityPlayerMP) player).connection.sendPacket(new SPacketSetSlot(-1, 0, mouseStack));
            return false;
        }
    }
}
