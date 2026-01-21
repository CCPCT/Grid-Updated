package de.guntram.mcmod.grid;

import de.guntram.mcmod.grid.modConfig.ConfigScreen;
import de.guntram.mcmod.grid.modConfig.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static org.lwjgl.glfw.GLFW.*;

public class Grid implements ClientModInitializer
{
    static final String MODID="grid";
    static final String MODNAME="Grid";
    static final String VERSION="@VERSION@";
    public static Grid instance;
    
    public static final int Y_FOR_FLOAT=-64;
    
    private int gridX=16;
    private int gridZ=16;
    private int fixY=Y_FOR_FLOAT;
    private int offsetX=0;
    private int offsetZ=0;
    private int distance=30;
    private int lightLevel=1;
    private boolean showGrid=false;
    private boolean isBlocks=true;
    private boolean isCircles=false;
    private boolean isHexes = false;
    private boolean showSpawns=false;
    private Pattern showBiomes=null;
    private boolean showSlimes = false;
    private long slimeSeed = 0;
    private Logger LOGGER;
    
    private float[] blockColor      = colorToRgb(0x8080ff);
    private float[] lineColor       = colorToRgb(0xff8000);
    private float[] circleColor     = colorToRgb(0x00e480);
    private float[] spawnNightColor = colorToRgb(0xffff00);
    private float[] spawnDayColor   = colorToRgb(0xff0000);
    private float[] biomeColor      = colorToRgb(0xff00ff);
    private float[] slimeColor      = colorToRgb(0x00ff00);
    
    private static final String modes[] = { "grid.displaymode.rectangle", "grid.displaymode.circle", "grid.displaymode.hex" };
    private boolean settingsRequested;

    KeyBinding showHide, gridHere, gridFixY, gridSpawns, gridSettings;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;
    
    private Displaycache[][] biomeCache;
    private Displaycache[][] spawnCache;
    int biomeUpdateX, spawnUpdateX;


    // This should have been done with stack.translate(camera.?), but the camera coords can easily exceed
    // good precision values for floats, and the MatrixStack consists of float matrices. So don't use translate,
    // subtract these values when calling vertex() instead.
    private double cameraX, cameraY, cameraZ;
    
    class Displaycache {
        byte displaymode;
        int ycoord;
        
        Displaycache(byte mode, int y) {
            this.displaymode = mode;
            this.ycoord = y;
        }
    }

    @Override
    public void onInitializeClient() {
        instance=this;
        ModConfig.load();

        biomeCache = new Displaycache[256][];
        spawnCache = new Displaycache[256][];
        for (int i=0; i<256; i++) {
            spawnCache[i]=new Displaycache[256];
            biomeCache[i]=new Displaycache[256];
        }

        setKeyBindings();
        registerCommands();
        LOGGER = LogManager.getLogger(MODNAME);
    }
    
    private float[] colorToRgb(int color) {
        float[] result = new float[3];
        result[0] = ((color>>16)&0xff) / 255f;
        result[1] = ((color>> 8)&0xff) / 255f;
        result[2] = ((color>> 0)&0xff) / 255f;
        return result;
    }
    
    public void renderOverlay(float partialTicks, VertexConsumer consumer, double cameraX, double cameraY, double cameraZ) {
        
        if (!showGrid && !showSpawns && !showSlimes && showBiomes == null)
            return;
        
        blockColor =        colorToRgb(ModConfig.get().blockColor);
        lineColor  =        colorToRgb(ModConfig.get().lineColor);
        circleColor=        colorToRgb(ModConfig.get().circleColor);
        spawnNightColor =   colorToRgb(ModConfig.get().spawnNightColor);
        spawnDayColor =     colorToRgb(ModConfig.get().spawnDayColor);
        biomeColor =        colorToRgb(ModConfig.get().biomeColor);
        slimeColor =        colorToRgb(ModConfig.get().slimeColor);

        Entity player = MinecraftClient.getInstance().getCameraEntity();
        // don't translate, subtract manaully in vertex()
        // stack.translate(-cameraX, -cameraY, -cameraZ);
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;

        int playerX=(int) Math.floor(player.getX());
        int playerZ=(int) Math.floor(player.getZ());
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);
        int baseX=playerX-playerXShift;
        int baseZ=playerZ-playerZShift;
        int sizeX=Math.max((distance/gridX)*gridX, 2*gridX);
        int sizeZ=Math.max((distance/gridZ)*gridZ, 2*gridZ);
        if (playerXShift > sizeX/2) { baseX += gridX; }
        if (playerZShift > sizeZ/2) { baseZ += gridZ; }

        thisDumpTime=System.currentTimeMillis();
        dump=false;
        if (thisDumpTime > lastDumpTime + 50000) {
            dump=false;         // set this to true to get line info from time to time
            lastDumpTime=thisDumpTime;
        }
        
        if (showGrid) {
            double tempy=((fixY==Y_FOR_FLOAT ? player.lastRenderY + (player.getY() - player.lastRenderY) * (double)partialTicks : fixY));
            final double y;
            if (player.getY()+player.getEyeHeight(player.getPose()) > tempy) {
                y=tempy+0.05f;
            } else {
                y=tempy-0.05f;
            }

            int circRadSquare=(gridX/2)*(gridX/2);
            if (isBlocks) {
                for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                    for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                        if (isHexes) {
                            if (gridX >= gridZ) {
                                drawXTriangleVertex(consumer, x, y, z,                   true,  blockColor[0], blockColor[1], blockColor[2]);       //  dot itself
                                drawXTriangleVertex(consumer, x-gridZ/4f, y, z-gridZ/2f, false, blockColor[0], blockColor[1], blockColor[2]);       // left bottom of myself red
                                drawXTriangleVertex(consumer, x+gridX/2f-gridZ/4f, y, z, false, blockColor[0], blockColor[1], blockColor[2]);       // node right orange
                                drawXTriangleVertex(consumer, x+gridX/2f, y, z-gridZ/2f, true,  blockColor[0], blockColor[1], blockColor[2]);       // right bottom of right node yellowgreenish
                            } else {
                                drawYTriangleVertex(consumer, x, y, z,                   true,  blockColor[0], blockColor[1], blockColor[2]);       //  dot itself
                                drawYTriangleVertex(consumer, x-gridX/2f, y, z-gridX/4f, false, blockColor[0], blockColor[1], blockColor[2]);       // left bottom of myself red
                                drawYTriangleVertex(consumer, x, y, z+gridZ/2f-gridX/4f, false, blockColor[0], blockColor[1], blockColor[2]);       // node right orange
                                drawYTriangleVertex(consumer, x-gridX/2f, y, z+gridZ/2f, true,  blockColor[0], blockColor[1], blockColor[2]);       // right bottom of right node yellowgreenish
                            }
                        } else {
                            drawSquare(consumer, x, y, z, blockColor[0], blockColor[1], blockColor[2]);
                        }
                        if (isCircles) {
                            int dx=0;
                            int dz=gridX/2;
                            for (;;) {
                                int nextx=dx+1;
                                int nextz=dz;
                                int toomuch=(nextx*nextx)+(nextz*nextz)-circRadSquare;
                                if (nextz>0 && (nextz-1)*(nextz-1)+(nextx*nextx)>circRadSquare-toomuch)
                                    nextz--;
                                if (nextz<nextx) {
                                    drawCircleSegment(consumer, x, dx, dz, y, z, dz, dx, circleColor[0], circleColor[1], circleColor[2]);
                                    break;
                                }

                                if (dump) {
                                    System.out.println("circle line from "+dx+"/"+dz+" to "+nextx+"/"+nextz+
                                            ", (x/2)^2="+((gridX/2.0)*(gridX/2.0))+
                                            ", dist is "+(nextx*nextx+nextz*nextz)+
                                            ", one higher dist is "+(nextx*nextx+((nextz+1)*(nextz+1)))+
                                            ", one lower dist is "+(nextx*nextx+((nextz-1)*(nextz-1)))
                                            );
                                }
                                drawCircleSegment(consumer, x, dx, nextx, y, z, dz, nextz, circleColor[0], circleColor[1], circleColor[2]);
                                dx=nextx;
                                dz=nextz;
                            }
                        }
                        dump=false;
                    }
                }
            } else {
                if (isHexes) {
                    for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                            if (gridX >= gridZ) {
                                drawLine(consumer, x+0.5f, x+0.5f-gridZ/4f,                     y, y, z+0.5f, z+0.5f-gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to LB
                                drawLine(consumer, x+0.5f, x+0.5f-gridZ/4f,                     y, y, z+0.5f, z+0.5f+gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to LT
                                drawLine(consumer, x+0.5f, x+0.5f+gridX/2f-gridZ/4f,            y, y, z+0.5f, z+0.5f,                   lineColor[0], lineColor[1], lineColor[2]);   // to R
                                drawLine(consumer, x+0.5f+gridX/2f-gridZ/4f, x+0.5f+gridX/2f,   y, y, z+0.5f, z+0.5f-gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to RB
                                drawLine(consumer, x+0.5f+gridX/2f-gridZ/4f, x+0.5f+gridX/2f,   y, y, z+0.5f, z+0.5f+gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to RT
                                
                                drawLine(consumer, x+0.5f+gridX/2f, x+0.5f+gridX-gridZ/4f,      y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ/2f, lineColor[0], lineColor[1], lineColor[2]);   // left stump out
                                drawLine(consumer, x+0.5f-gridZ/4f, x+0.5f-gridX/2f,            y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ/2f, lineColor[0], lineColor[1], lineColor[2]);   // right stump out
                            } else {
                                drawLine(consumer, x+0.5f, x+0.5f-gridX/2f,          y, y, z+0.5f, z+0.5f-gridX/4f,                     lineColor[0], lineColor[1], lineColor[2]);   // to LT
                                drawLine(consumer, x+0.5f, x+0.5f+gridX/2f,          y, y, z+0.5f, z+0.5f-gridX/4f,                     lineColor[0], lineColor[1], lineColor[2]);   // to RT
                                drawLine(consumer, x+0.5f, x+0.5f,                   y, y, z+0.5f, z+0.5f+gridZ/2f-gridX/4f,            lineColor[0], lineColor[1], lineColor[2]);   // to B
                                drawLine(consumer, x+0.5f, x+0.5f-gridX/2f,          y, y, z+0.5f+gridZ/2f-gridX/4f, z+0.5f+gridZ/2f,   lineColor[0], lineColor[1], lineColor[2]);   // to LB
                                drawLine(consumer, x+0.5f, x+0.5f+gridX/2f,          y, y, z+0.5f+gridZ/2f-gridX/4f, z+0.5f+gridZ/2f,   lineColor[0], lineColor[1], lineColor[2]);   // to RB
                                
                                drawLine(consumer, x+0.5f+gridX/2f, x+0.5f+gridX/2f, y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ-gridX/4f,      lineColor[0], lineColor[1], lineColor[2]);   // stump up
                                drawLine(consumer, x+0.5f+gridX/2f, x+0.5f+gridX/2f, y, y, z+0.5f-gridX/4f, z+0.5f-gridZ/2f,            lineColor[0], lineColor[1], lineColor[2]);   // stump down
                            }
                        }
                    }
                } else if (isCircles) {
                    for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                            double dx=0;
                            double dz=gridX/2.0f;
                            for (double nextx=0.1f; nextx<gridX; nextx+=0.1f) {
                                double nextz=(Math.sqrt(gridX*gridX/4.0-nextx*nextx));
                                drawCircleSegment(consumer, x, dx, nextx, y, z, dz, nextz, circleColor[0], circleColor[1], circleColor[2]);
                                dx=nextx;
                                dz=nextz;
                                if (nextz<nextx)
                                    break;
                            }
                            dump=false;
                        }   
                    }
                } else {
                    drawLineGrid(consumer, baseX, baseZ, y, sizeX, sizeZ);
                }
            }
        }
        
        if (showSpawns) {
            showSpawns(consumer, player, player.getBlockPos().getX(), player.getBlockPos().getZ());
        }
        
        if (showBiomes!=null) {
            showBiomes(consumer, player, player.getBlockPos().getX(), player.getBlockPos().getZ());
        }

        if (showSlimes) {
            showSlimes(consumer, player, player.getBlockPos().getX(), player.getBlockPos().getZ());
        }
    }
    
    private void showSpawns(VertexConsumer consumer, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-64;
        int maxy=(int)(player.getY())+2;

        World playerWorld = MinecraftClient.getInstance().world;
        if (miny<playerWorld.getBottomY()) { miny=playerWorld.getBottomY(); }
        if (maxy>playerWorld.getTopYInclusive()-1)  { maxy=playerWorld.getTopYInclusive()-1; }

        WorldChunk cachedChunk = null;

        spawnUpdateX++;
        if (spawnUpdateX < (baseX-distance) || spawnUpdateX > baseX+distance) {
            spawnUpdateX = baseX-distance;
        }
        boolean alwaysUpdate = !ModConfig.get().useCache;

        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                
                Displaycache display = null;
                if (alwaysUpdate || x == spawnUpdateX) {
                    if (cachedChunk == null || cachedChunk.getPos().x != (x>>4) || cachedChunk.getPos().z != (z>>4)) {
                        cachedChunk=playerWorld.getChunk(x>>4, z>>4);
                    }
                    boolean foundAir = false;
                    for (int y=maxy; y>=miny; y--) {

                        BlockState state;
                        BlockPos pos=new BlockPos(x, y, z);
                        state = cachedChunk.getBlockState(pos);
                        /* if (x == 322 && z == 38) {
                            System.out.printf("At 322/38 y=%d, foundAir=%s, state=%s, isSolid=%s\n",
                                    y, ((Boolean)foundAir).toString(), state.getBlock().getName().getString(), state.isSolidBlock(player.world, pos));
                        } */
                        if (state.isSolidBlock(playerWorld, pos)) {
                            if (foundAir && y != maxy) {
                                BlockPos up = pos.up();
                                // if (SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, player.world, up, EntityType.CREEPER)) {
                                    if (playerWorld.getLightLevel(LightType.BLOCK, up)>=lightLevel)
                                        display = new Displaycache((byte)0, y);
                                    else if (playerWorld.getLightLevel(LightType.SKY, up)>=lightLevel)
                                        display = new Displaycache((byte)1, y);
                                    else
                                        display = new Displaycache((byte)2, y);
                                // }
                            }
                            if (foundAir) {
                                break;
                            }
                        } else {
                            foundAir = true;
                        }
                    }
                    spawnCache[x&0xff][z&0xff] = display;
                } else {
                    display = spawnCache[x&0xff][z&0xff];
                }
                if (display == null) {
                    continue;
                }

                if (display.displaymode == 1) {
                    drawCross(consumer, x, display.ycoord+1.05f, z, spawnNightColor[0], spawnNightColor[1], spawnNightColor[2], false );
                } else if (display.displaymode == 2) {
                    drawCross(consumer, x, display.ycoord+1.05f, z, spawnDayColor[0], spawnDayColor[1], spawnDayColor[2], true );
                }
            }
        }
    }

    private void showSlimes(VertexConsumer consumer, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-64;
        int maxy=(int)(player.getY())+2;

        World playerWorld = MinecraftClient.getInstance().world;
        if (miny<playerWorld.getBottomY()) { miny=playerWorld.getBottomY(); }
        if (maxy>playerWorld.getTopYInclusive()-1)  { maxy=playerWorld.getTopYInclusive()-1; }

        int chunkx = Integer.MAX_VALUE;
        int chunkz = Integer.MAX_VALUE;
        boolean isSlimeChunk = false;
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z = baseZ - distance; z <= baseZ + distance; z++) {
                if (x/16 != chunkx || z/16 != chunkz) {
                    chunkx = x>>4;
                    chunkz = z>>4;
                    isSlimeChunk = ChunkRandom.getSlimeRandom(chunkx, chunkz, slimeSeed, 987234911L).nextInt(10) == 0;
                    // LOGGER.info("at chunkx {} chunkz {} isslime = {}", chunkx, chunkz, isSlimeChunk);
                }
                if (!isSlimeChunk) {
                    continue;
                }
                int y;
                if (fixY == Y_FOR_FLOAT) {
                    y = (int) (player.getY());
                    while (y >= miny && isAir(playerWorld.getBlockState(new BlockPos(x, y, z)).getBlock())) {
                        y--;
                    }
                } else {
                    y = fixY - 1;
                }
                // LOGGER.info("drawing slime diamond at {} {} {}", x, y, z);
                drawDiamond(consumer, x, y+1, z, slimeColor[0], slimeColor[1], slimeColor[2]);
            }
        }
    }
    
    private void showBiomes(VertexConsumer consumer, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY());
        World playerWorld = MinecraftClient.getInstance().world;
        if (miny<playerWorld.getBottomY()) { miny=playerWorld.getBottomY(); }
        if (maxy>playerWorld.getTopYInclusive()-1)  { maxy=playerWorld.getTopYInclusive()-1; }

        biomeUpdateX++;
        if (biomeUpdateX < (baseX-distance) || biomeUpdateX > baseX+distance) {
            biomeUpdateX = baseX-distance;
        }
        boolean alwaysUpdate = !ModConfig.get().useCache;
        
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                Displaycache display = null;
                if (alwaysUpdate || x == biomeUpdateX) {
                    // 2 lines stolen from DebugHud.java
                    RegistryEntry<Biome> biome = playerWorld.getBiome(new BlockPos(x, 64, z));
                    String biomeName = biome.getKeyOrValue().map(key -> key.getValue().toString(), value -> "[unregistered "+value+"]");
                    boolean match = showBiomes.matcher(biomeName).find();
                    if (match) {
                        int y=(int)(player.getY());
                        while (y>=miny && isAir(playerWorld.getBlockState(new BlockPos(x, y, z)).getBlock())) {
                            y--;
                        }
                        display = new Displaycache((byte)1, y);
                    }
                    biomeCache[x&0xff][z&0xff] = display;
                } else {
                    display = biomeCache[x&0xff][z&0xff];
                }
                if (display != null && display.displaymode != 0) {
                    int y;
                    if (fixY == Y_FOR_FLOAT) {
                        y=display.ycoord;
                    } else {
                        y=fixY-1;
                    }
                    drawDiamond(consumer, x, y+1, z, biomeColor[0], biomeColor[1], biomeColor[2]);
                }
            }
        }
    }
    
    static private boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }
    
    private void drawLineGrid(VertexConsumer consumer, int baseX, int baseZ, double y, int sizeX, int sizeZ) {
        for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
            drawLine(consumer, x, x, y, y, baseZ-distance, baseZ+distance, lineColor[0], lineColor[1], lineColor[2]);
        }
        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
            drawLine(consumer, baseX-distance, baseX+distance, y, y, z, z, lineColor[0], lineColor[1], lineColor[2]);
        }
    }
    
    private void drawSquare(VertexConsumer consumer, double x, double y, double z, float r, float g, float b) {
        drawLine(consumer, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.3f, r, g, b);
        drawLine(consumer, x+0.7f, x+0.7f, y, y, z+0.3f, z+0.7f, r, g, b);
        drawLine(consumer, x+0.7f, x+0.3f, y, y, z+0.7f, z+0.7f, r, g, b);
        drawLine(consumer, x+0.3f, x+0.3f, y, y, z+0.7f, z+0.3f, r, g, b);
    }
    
    private void drawXTriangleVertex(VertexConsumer consumer, double x, double y, double z, boolean inverted, float r, float g, float b) {
        double xMult = (inverted ? 1 : -1);
        drawLine(consumer, x+0.5f, x+0.5f+ 0.5f*xMult, y, y, z+0.5f, z+0.5f, r, g, b);
        drawLine(consumer, x+0.5f, x+0.5f-0.25f*xMult, y, y, z+0.5f, z+1.0f, r, g, b);
        drawLine(consumer, x+0.5f, x+0.5f-0.25f*xMult, y, y, z+0.5f, z+0.0f, r, g, b);
    }

    private void drawYTriangleVertex(VertexConsumer consumer, double x, double y, double z, boolean inverted, float r, float g, float b) {
        double xMult = (inverted ? 1 : -1);
        drawLine(consumer, x+0.5f, x+0.5f, y, y, z+0.5f, z+0.5f+ 0.5f*xMult, r, g, b);
        drawLine(consumer, x+0.5f, x+1.0f, y, y, z+0.5f, z+0.5f-0.25f*xMult, r, g, b);
        drawLine(consumer, x+0.5f, x+0.0f, y, y, z+0.5f, z+0.5f-0.25f*xMult, r, g, b);
    }

    
    private void drawCircleSegment(VertexConsumer consumer, double xc, double x1, double x2, double y, double zc, double z1, double z2, float red, float green, float blue) {
        drawLine(consumer, xc+x1+0.5f, xc+x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        drawLine(consumer, xc-x1+0.5f, xc-x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        drawLine(consumer, xc+x1+0.5f, xc+x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        drawLine(consumer, xc-x1+0.5f, xc-x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        drawLine(consumer, xc+z1+0.5f, xc+z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        drawLine(consumer, xc-z1+0.5f, xc-z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        drawLine(consumer, xc+z1+0.5f, xc+z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
        drawLine(consumer, xc-z1+0.5f, xc-z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
    }

    private void drawLine(VertexConsumer consumer, double x1, double x2, double y1, double y2, double z1, double z2, float red, float green, float blue) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double invDist = MathHelper.inverseSqrt(dx*dx + dy*dy + dz*dz);

        if (invDist < 1000) {
            dx *= invDist;
            dy *= invDist;
            dz *= invDist;
        }

        // 3. Pass the resulting 3 floats to the consumer
        consumer.vertex((float)(x1 - cameraX), (float)(y1 - cameraY), (float)(z1 - cameraZ))
                .color(red, green, blue, 1.0f)
                .normal((float)dx, (float)dy, (float)dz);

        consumer.vertex((float)(x2 - cameraX), (float)(y2 - cameraY), (float)(z2 - cameraZ))
                .color(red, green, blue, 1.0f)
                .normal((float)dx, (float)dy, (float)dz);
    }
    
    private void drawCross(VertexConsumer consumer, double x, double y, double z, float red, float green, float blue, boolean twoLegs) {
        drawLine(consumer, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.7f, red, green, blue);
        if (twoLegs) {
            drawLine(consumer, x+0.3f, x+0.7f, y, y, z+0.7f, z+0.3f, red, green, blue);
        }
    }
    
    private void drawDiamond(VertexConsumer consumer, double x, double y, double z, float red, float green, float blue) {
        double x1 = x+0.3f;
        double x2 = x+0.5f;
        double x3 = x+0.7f;
        double z1 = z+0.3f;
        double z2 = z+0.5f;
        double z3 = z+0.7f;
        double y1 = y+0.05f;
        
        drawLine(consumer, x1, x2, y1, y1, z2, z1, red, green, blue);
        drawLine(consumer, x2, x3, y1, y1, z1, z2, red, green, blue);
        drawLine(consumer, x3, x2, y1, y1, z2, z3, red, green, blue);
        drawLine(consumer, x2, x1, y1, y1, z3, z2, red, green, blue);
    }
    
    private void cmdShow(ClientPlayerEntity sender) {
        showGrid = true;
        sender.sendMessage(Text.literal(I18n.translate("msg.gridshown", (Object[]) null)), false);
    }
    
    private void cmdHide(ClientPlayerEntity sender) {
        showGrid = false;
        sender.sendMessage(Text.literal(I18n.translate("msg.gridhidden", (Object[]) null)), false);
    }
    
    private void cmdSpawns(ClientPlayerEntity sender, String newLevel) {
        if (newLevel != null) {
            int level=1;
            try {
                level=Integer.parseInt(newLevel);
            } catch (NumberFormatException | NullPointerException ignored) {
                ;
            }
            if (level<=0 || level>15)
                level=1;
            this.lightLevel=level;
        }
        if (showSpawns && newLevel==null) {
            sender.sendMessage(Text.literal(I18n.translate("msg.spawnshidden")), false);
            showSpawns=false;
        } else {
            sender.sendMessage(Text.literal(I18n.translate("msg.spawnsshown", this.lightLevel)), false);
            showSpawns=true;
        }
    }
    
    private void cmdLines(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = false;
        sender.sendMessage(Text.literal(I18n.translate("msg.gridlines", (Object[]) null)), false);
    }
    
    private void cmdBlocks(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = true;
        sender.sendMessage(Text.literal(I18n.translate("msg.gridblocks", (Object[]) null)), false);
    }
    
    private void cmdCircles(ClientPlayerEntity sender) {
        if (isCircles) {
            isCircles = false;
            sender.sendMessage(Text.literal(I18n.translate("msg.gridnomorecircles", (Object[]) null)), false);
        } else {
            isCircles = true;
            isHexes = false;
            showGrid = true;
            sender.sendMessage(Text.literal(I18n.translate("msg.gridcircles", (Object[]) null)), false);
        }
    }
    
    private void cmdHere(ClientPlayerEntity sender) {
        int playerX=(int) Math.floor(sender.getX());
        int playerZ=(int) Math.floor(sender.getZ());
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);                
        offsetX=playerXShift;
        offsetZ=playerZShift;
        showGrid=true;
        sender.sendMessage(Text.literal(I18n.translate("msg.gridaligned", (Object[]) null)), false);
    }
    
    private void cmdHex(ClientPlayerEntity sender) {
        if (isHexes) {
            isHexes = false;
        } else {
            isHexes = true;
            isCircles = false;
            showGrid = true;
        }
    }
    
    private void cmdFixy(ClientPlayerEntity sender) {
        if (fixY==Y_FOR_FLOAT) {
            cmdFixy(sender, (int)Math.floor(sender.getY()));
        } else {
            fixY=Y_FOR_FLOAT;
            sender.sendMessage(Text.literal(I18n.translate("msg.gridheightfloat")), false);
        }
    }

    private void cmdFixy(ClientPlayerEntity sender, int level) {
            fixY=level;
            sender.sendMessage(Text.literal(I18n.translate("msg.gridheightfixed", fixY)), false);
    }
    
    private void cmdChunks(ClientPlayerEntity sender) {
        offsetX=offsetZ=0;
        gridX=gridZ=16;
        showGrid=true;
        sender.sendMessage(Text.literal(I18n.translate("msg.gridchunks")), false);
    }
    
    private void cmdDistance(ClientPlayerEntity sender, int distance) {
        this.distance=distance;
        sender.sendMessage(Text.literal(I18n.translate("msg.griddistance", distance)), false);
    }
    
    private void cmdX(ClientPlayerEntity sender, int coord) {
        cmdXZ(sender, coord, gridZ);
    }

    private void cmdZ(ClientPlayerEntity sender, int coord) {
        cmdXZ(sender, gridX, coord);
    }
    
    private void cmdXZ(ClientPlayerEntity sender, int newX, int newZ) {
        if (newX>0 && newZ>0) {
            gridX=newX;
            gridZ=newZ;
            showGrid=true;
        	sender.sendMessage(Text.literal(I18n.translate("msg.gridpattern", gridX, gridZ)), false);
        } else {
            sender.sendMessage(Text.literal(I18n.translate("msg.gridcoordspositive")), false);
        }
    }

    private void cmdSlime(ClientPlayerEntity player, boolean show) {
        if (!show) {
            cmdSlime(player, show, 0l);
            return;
        } else if (MinecraftClient.getInstance().isConnectedToLocalServer()) {
            IntegratedServer server = MinecraftClient.getInstance().getServer();
            long seed = server.getWorld(MinecraftClient.getInstance().world.getRegistryKey()).getSeed();
            cmdSlime(player, true, seed);
        } else {
            player.sendMessage(Text.translatable("msg.gridnoseed"),false);
        }
    }

    private void cmdSlime(ClientPlayerEntity player, boolean show, long seed) {
        World world = MinecraftClient.getInstance().world;
        if (show) {
            player.sendMessage(Text.translatable("msg.gridslimeon", seed),false);
            showSlimes = true;
            slimeSeed = seed;
        } else {
            player.sendMessage(Text.translatable("msg.gridslimeoff"),false);
            showSlimes = false;
        }
    }
    
    private void cmdBiome(ClientPlayerEntity sender, String biome) {
        if (biome == null  || biome.isEmpty()) {
            showBiomes = null;
        } else {
            try {
                this.showBiomes=Pattern.compile(biome, Pattern.CASE_INSENSITIVE);
                sender.sendMessage(Text.literal(I18n.translate("msg.biomesearching", biome)), false);
            } catch (PatternSyntaxException ex) {
                showBiomes = null;
                sender.sendMessage(Text.literal(I18n.translate("msg.biomepatternbad", biome)), false);
            }
        }
    }
    
//    private void cmdSettings() {
//
//        runtimeSettings = new VolatileConfiguration();
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.x", "", gridX, gridX, 1, 64, (val) -> gridX=(int) val));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.z", "", gridZ, gridZ, 1, 64, (val) -> gridZ=(int) val));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.y", "", fixY, fixY, Y_FOR_FLOAT-1, 383, (val) -> fixY =(int) val));           // Min is actually -1 but due to how rounding negatives work we need -2 to have -1 shown.
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.distance", "", distance, 30, 16, 255, (val) -> distance=(int) val));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.lightlevel", "", lightLevel, 1, 1, 15, (val) -> lightLevel=(int) val));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.showgrid", "", showGrid, false, null, null, (val) -> showGrid=(boolean) val));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.isblocks", "", isBlocks, true, null, null, (val) -> isBlocks=(boolean) val));
//        runtimeSettings.addItem(new ConfigurationSelectList("grid.settings.displaymode", "", modes, 0 + (isCircles ? 1 : 0) + (isHexes ? 2 : 0), 0, (val) -> {
//            isCircles = ((int)val == 1); isHexes = ((int)val == 2);
//        }));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.showspawn", "", showSpawns, false, null, null, (val) -> showSpawns = (boolean) val));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.showbiomes", "", (showBiomes != null ? showBiomes.pattern() : ""), "", null, null,
//                (val) -> instance.cmdBiome(MinecraftClient.getInstance().player, (String) val)));
//        runtimeSettings.addItem(new ConfigurationItem("grid.settings.showslimes", "", (showSlimes), false, null, null,
//                (val) -> instance.cmdSlime(MinecraftClient.getInstance().player, (Boolean) val)));
//
//        Screen screen = GuiModOptions.getGuiModOptions(null, "Grid Settings", this);
//        MinecraftClient.getInstance().setScreen(screen);
//    }

    public void registerCommands() {
         ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                literal("grid")
                    .then(
                        literal("show").executes(c->{
                            instance.cmdShow(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("hide").executes(c->{
                            instance.cmdHide(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("lines").executes(c->{
                            instance.cmdLines(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("blocks").executes(c->{
                            instance.cmdBlocks(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("circles").executes(c->{
                            instance.cmdCircles(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("here").executes(c->{
                            instance.cmdHere(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("hex").executes(c->{
                            instance.cmdHex(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("slime").then(
                                argument("seed", longArg()).executes(c->{
                                    instance.cmdSlime(MinecraftClient.getInstance().player, !instance.showSlimes, getLong(c, "seed"));
                                    return 1;
                                })
                        ). executes(c->{
                            instance.cmdSlime(MinecraftClient.getInstance().player, !instance.showSlimes);
                            return 1;
                        })
                    )
                    .then(
                        literal("fixy").then(
                            argument("y", integer()).executes(c->{
                                instance.cmdFixy(MinecraftClient.getInstance().player, getInteger(c, "y"));
                                return 1;
                            })
                        ).executes(c->{
                            instance.cmdFixy(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("chunks").executes(c->{
                            instance.cmdChunks(MinecraftClient.getInstance().player);
                            return 1;
                        })
                    )
                    .then(
                        literal("spawns").then(
                                                argument("lightlevel", integer()).executes(c->{
                                                            instance.cmdSpawns(MinecraftClient.getInstance().player, ""+getInteger(c, "lightlevel"));
                                return 1;
                                                    })
                                            ).executes(c->{
                            instance.cmdSpawns(MinecraftClient.getInstance().player, null);
                            return 1;
                        })
                    )
                    .then(
                        literal("distance").then (
                            argument("distance", integer()).executes(c->{
                                instance.cmdDistance(MinecraftClient.getInstance().player, getInteger(c, "distance"));
                                return 1;
                            })
                        )
                    )
                    .then(
                        argument("x", integer()).then (
                            argument("z", integer()).executes(c->{
                                instance.cmdXZ(MinecraftClient.getInstance().player, getInteger(c, "x"), getInteger(c, "z"));
                                return 1;
                            })
                        ).executes(c->{
                            instance.cmdXZ(MinecraftClient.getInstance().player, getInteger(c, "x"), getInteger(c, "x"));
                            return 1;
                        })
                    )
                    .then(
                        literal("biome").then(
                                                argument("pattern", string()).executes(c->{
                                                            instance.cmdBiome(MinecraftClient.getInstance().player, ""+getString(c, "pattern"));
                                return 1;
                                                    })
                                            ).executes(c->{
                            instance.cmdBiome(MinecraftClient.getInstance().player, null);
                            return 1;
                        })
                    )
                    .then (
                        literal("settings").executes(c->{
                            // This can't open the settings screen directly because
                            // MC will call closeScreen to close the chat hud.
                            instance.settingsRequested = true;
                            return 1;
                        })
                    )
            );
        });
    }

    public void setKeyBindings() {
        final KeyBinding.Category category=KeyBinding.Category.create(Identifier.of("key.categories.grid"));
        KeyBindingHelper.registerKeyBinding(showHide = new KeyBinding("key.grid.showhide", InputUtil.Type.KEYSYM, GLFW_KEY_B, category));
        KeyBindingHelper.registerKeyBinding(gridHere = new KeyBinding("key.grid.here", InputUtil.Type.KEYSYM, GLFW_KEY_C, category));
        KeyBindingHelper.registerKeyBinding(gridFixY = new KeyBinding("key.grid.fixy", InputUtil.Type.KEYSYM, GLFW_KEY_Y, category));
        KeyBindingHelper.registerKeyBinding(gridSpawns = new KeyBinding("key.grid.spawns", InputUtil.Type.KEYSYM, GLFW_KEY_L, category));
        KeyBindingHelper.registerKeyBinding(gridSettings = new KeyBinding("key.grid.settings", InputUtil.Type.KEYSYM, GLFW_KEY_G, category));
        ClientTickEvents.END_CLIENT_TICK.register(e->processKeyBinds());
    }

    public void processKeyBinds() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (showHide.wasPressed()) {
            showGrid=!showGrid;
        }
        if (gridFixY.wasPressed()) {
            cmdFixy(player);
        }
        if (gridHere.wasPressed()) {
            cmdHere(player);
        }
        if (gridSpawns.wasPressed()) {
            cmdSpawns(player, null);
        }
        if (settingsRequested || gridSettings.wasPressed()) {
            settingsRequested = false;
            MinecraftClient client = MinecraftClient.getInstance();
            client.setScreen(ConfigScreen.getConfigScreen(client.currentScreen));
        }
    }
}
