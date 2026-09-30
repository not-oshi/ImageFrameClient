package com.loohp.imageframe;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.loohp.imageframe.configuration.Configuration;
import com.loohp.imageframe.object.ImageMapData;
import com.loohp.imageframe.object.MapTooltipComponent;
import com.loohp.imageframe.object.MultipartHdMapInfo;
import com.loohp.imageframe.payload.ClientboundAcknowledgement;
import com.loohp.imageframe.payload.ClientboundHdImageMultipartResponse;
import com.loohp.imageframe.payload.ClientboundHdImageResponse;
import com.loohp.imageframe.payload.ClientboundImageMapDetailsResponse;
import com.loohp.imageframe.payload.ClientboundImageUpdatedSignal;
import com.loohp.imageframe.payload.ServerboundAcknowledgement;
import com.loohp.imageframe.payload.ServerboundHdImageRequest;
import com.loohp.imageframe.payload.ServerboundImageMapDetailsRequest;
import com.mojang.blaze3d.platform.NativeImage;
import eu.midnightdust.lib.config.MidnightConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(value = ImageFrameClient.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ImageFrameClient.MOD_ID, value = Dist.CLIENT)
public class ImageFrameClient {

    public static final String MOD_ID = "imageframeclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ImageFrameClient MOD;

    private final AtomicBoolean currentServerSupported = new AtomicBoolean(false);
    private final Int2ObjectMap<Optional<Identifier>> loadedHdImages = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Optional<ImageMapData>> imageMapData = new Int2ObjectOpenHashMap<>();
    private final Cache<Integer, MultipartHdMapInfo> pendingMultipart = CacheBuilder.newBuilder().expireAfterAccess(Duration.of(10, ChronoUnit.SECONDS)).build();

    public ImageFrameClient(ModContainer container) {
        MOD = this;
        LOGGER.info("Hello world from ImageFrame Client!");
        MidnightConfig.init(MOD_ID, Configuration.class);

        // Makes the MidnightConfig screen available from the mods screen of the game.
        IConfigScreenFactory configScreenFactory = (mod, parent) -> MidnightConfig.getScreen(parent, MOD_ID);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }

    /**
     * Registers the payload types sent by the ImageFrame server mod.
     * <p>
     * Every payload is registered as optional, so that clients running this mod can still join
     * servers which do not have ImageFrame installed.
     */
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(ClientboundAcknowledgement.ID, ClientboundAcknowledgement.CODEC);
        registrar.playToClient(ClientboundHdImageResponse.ID, ClientboundHdImageResponse.CODEC);
        registrar.playToClient(ClientboundHdImageMultipartResponse.ID, ClientboundHdImageMultipartResponse.CODEC);
        registrar.playToClient(ClientboundImageUpdatedSignal.ID, ClientboundImageUpdatedSignal.CODEC);
        registrar.playToClient(ClientboundImageMapDetailsResponse.ID, ClientboundImageMapDetailsResponse.CODEC);

        // These payloads are only ever sent to the server, the handlers are never invoked on this side.
        registrar.playToServer(ServerboundAcknowledgement.ID, ServerboundAcknowledgement.CODEC, (payload, context) -> {
        });
        registrar.playToServer(ServerboundHdImageRequest.ID, ServerboundHdImageRequest.CODEC, (payload, context) -> {
        });
        registrar.playToServer(ServerboundImageMapDetailsRequest.ID, ServerboundImageMapDetailsRequest.CODEC, (payload, context) -> {
        });
    }

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ClientboundAcknowledgement.ID, ImageFrameClient::onAcknowledgement);
        event.register(ClientboundHdImageResponse.ID, ImageFrameClient::onHdImageResponse);
        event.register(ClientboundHdImageMultipartResponse.ID, ImageFrameClient::onHdImageMultipartResponse);
        event.register(ClientboundImageUpdatedSignal.ID, ImageFrameClient::onImageUpdated);
        event.register(ClientboundImageMapDetailsResponse.ID, ImageFrameClient::onImageMapDetailsResponse);
    }

    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(MapTooltipComponent.class, component -> component);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MOD.onServerDisconnected();
    }

    private static void onAcknowledgement(ClientboundAcknowledgement payload, IPayloadContext context) {
        ClientPacketDistributor.sendToServer(new ServerboundAcknowledgement(payload.id()));
        MOD.currentServerSupported.set(true);
        if (Configuration.notifyWhenServerSupports) {
            SystemToast.add(
                    Minecraft.getInstance().gui.toastManager(),
                    SystemToast.SystemToastId.UNSECURE_SERVER_WARNING,
                    Component.translatable("imageframeclient.message.server_supported.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("imageframeclient.message.server_supported.description")
            );
        }
    }

    private static void onHdImageResponse(ClientboundHdImageResponse payload, IPayloadContext context) {
        if (Configuration.useNativeResMapImages) {
            try {
                int mapId = payload.mapId();
                if (payload.requestAccepted()) {
                    Optional<Integer> opt = payload.multipart();
                    byte[] data = payload.data();
                    if (opt.isPresent()) {
                        MultipartHdMapInfo info = new MultipartHdMapInfo();
                        info.put(0, data);
                        MOD.pendingMultipart.put(opt.get(), info);
                    } else {
                        if (data.length > 0) {
                            NativeImage nativeImage = MOD.resizeToPreference(NativeImage.read(data));
                            Identifier id = Identifier.fromNamespaceAndPath("imageframe", "hdmap_" + mapId);
                            DynamicTexture tex = new DynamicTexture(id::getPath, nativeImage);
                            Minecraft.getInstance().getTextureManager().register(id, tex);
                            MOD.loadedHdImages.put(mapId, Optional.of(id));
                        }
                    }
                } else {
                    MOD.loadedHdImages.remove(mapId);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void onHdImageMultipartResponse(ClientboundHdImageMultipartResponse payload, IPayloadContext context) {
        if (Configuration.useNativeResMapImages) {
            try {
                int mapId = payload.mapId();
                int multipartId = payload.multipart();
                MultipartHdMapInfo info = MOD.pendingMultipart.getIfPresent(multipartId);
                if (info != null) {
                    byte[] data = payload.data();
                    int index = payload.index();
                    if (data.length > 0) {
                        info.put(index, data);
                    }
                    if (payload.end()) {
                        info.setLastIndex(index);
                    }
                    if (info.isCompleted()) {
                        MOD.pendingMultipart.invalidate(multipartId);
                        NativeImage nativeImage = MOD.resizeToPreference(NativeImage.read(info.complete()));
                        Identifier id = Identifier.fromNamespaceAndPath("imageframe", "hdmap_" + mapId);
                        DynamicTexture tex = new DynamicTexture(id::getPath, nativeImage);
                        Minecraft.getInstance().getTextureManager().register(id, tex);
                        MOD.loadedHdImages.put(mapId, Optional.of(id));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void onImageUpdated(ClientboundImageUpdatedSignal payload, IPayloadContext context) {
        for (int index : payload.indexes()) {
            MOD.imageMapData.remove(index);
        }
        for (int mapId : payload.mapIds()) {
            Optional<Identifier> id = MOD.loadedHdImages.remove(mapId);
            if (id != null && id.isPresent()) {
                Minecraft.getInstance().getTextureManager().release(id.get());
            }
        }
    }

    private static void onImageMapDetailsResponse(ClientboundImageMapDetailsResponse payload, IPayloadContext context) {
        if (payload.width() > 0 && payload.height() > 0) {
            MOD.imageMapData.put(payload.index(), Optional.of(new ImageMapData(payload.width(), payload.height(), payload.mapIds())));
        }
    }

    private void onServerDisconnected() {
        imageMapData.clear();
        for (int mapId : new IntOpenHashSet(loadedHdImages.keySet())) {
            Optional<Identifier> id = loadedHdImages.remove(mapId);
            if (id != null && id.isPresent()) {
                Minecraft.getInstance().getTextureManager().release(id.get());
            }
        }
        currentServerSupported.set(false);
    }

    public NativeImage resizeToPreference(NativeImage src) {
        int size = Configuration.maxImageSize.getMaxSize();
        if (src.getWidth() <= size) {
            return src;
        }
        NativeImage dst = new NativeImage(size, size, false);
        src.resizeSubRectTo(0, 0, src.getWidth(), src.getHeight(), dst);
        return dst;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public Identifier getOrRequestLoadedHdMap(int mapId) {
        Optional<Identifier> result = loadedHdImages.get(mapId);
        if (result == null) {
            if (currentServerSupported.get()) {
                ServerboundHdImageRequest request = new ServerboundHdImageRequest(mapId);
                ClientPacketDistributor.sendToServer(request);
                loadedHdImages.put(mapId, Optional.empty());
            }
            return null;
        }
        return result.orElse(null);
    }

    public void clearLoadedHdMaps() {
        loadedHdImages.clear();
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public ImageMapData getOrRequestImageMapData(int index) {
        Optional<ImageMapData> result = imageMapData.get(index);
        if (result == null) {
            if (currentServerSupported.get()) {
                ServerboundImageMapDetailsRequest request = new ServerboundImageMapDetailsRequest(index);
                ClientPacketDistributor.sendToServer(request);
                imageMapData.put(index, Optional.empty());
            }
            return null;
        }
        return result.orElse(null);
    }
}
