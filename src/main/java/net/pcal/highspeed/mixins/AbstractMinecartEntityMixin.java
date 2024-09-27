package net.pcal.highspeed.mixins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.pcal.highspeed.HighspeedService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartEntityMixin {

    private static final double VANILLA_MAX_SPEED = 8.0 / 20.0;
    private static final double SQRT_TWO = 1.414213;
    Level level = this.level();
    private BlockPos lastPos = null;
    private double currentMaxSpeed = VANILLA_MAX_SPEED;
    private double lastMaxSpeed = VANILLA_MAX_SPEED;
    private Vec3 lastSpeedPos = null;
    private long lastSpeedTime = 0;
    private final AbstractMinecart minecart = (AbstractMinecart) (Object) this;

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        updateSpeedometer();
        clampVelocity();
    }

    @Redirect(method = "moveAlongTrack", at = @At(value = "INVOKE", ordinal = 0, target = "java/lang/Math.min(DD)D"))
    public double speedClamp(double d1, double d2) {
        final double maxSpeed = getModifiedMaxSpeed();
        return maxSpeed == VANILLA_MAX_SPEED ? Math.min(d1, d2) // i.e. preserve vanilla behavior
                : Math.min(maxSpeed * SQRT_TWO, d2);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    protected void getMaxSpeed(CallbackInfoReturnable<Double> cir) {
        final double maxSpeed = getModifiedMaxSpeed();
        if (maxSpeed != VANILLA_MAX_SPEED) {
            cir.setReturnValue(maxSpeed);
        }
    }

    private double getModifiedMaxSpeed() {
        final BlockPos currentPos = minecart.blockPosition();
        if (currentPos.equals(lastPos)) return currentMaxSpeed;
        lastPos = currentPos;
        // look at the *next* block the cart is going to hit
        final Vec3 v = minecart.getDeltaMovement();
        final BlockPos nextPos = new BlockPos(
                currentPos.getX() + Mth.sign(v.x()),
                currentPos.getY(),
                currentPos.getZ() + Mth.sign(v.z())
        );
        final BlockState nextState = minecart.level().getBlockState(nextPos);
        if (railShape.isAscending()
                && !level.getBlockState(new BlockPos(Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()))).is(BlockTags.RAILS)
                && !level.getBlockState(new BlockPos(Mth.floor(this.getX()), Mth.floor(this.getY() - 1.), Mth.floor(this.getZ()))).is(BlockTags.RAILS)) {

            if (level.getBlockState(new BlockPos(Mth.floor(this.getX()), Mth.floor(this.getY() - 2.), Mth.floor(this.getZ()))).is(BlockTags.RAILS)) {
                this.setPos(this.getX(), this.getY() - 1, this.getZ());
            } else if (level.getBlockState(new BlockPos(Mth.floor(this.getX()), Mth.floor(this.getY() - 3.), Mth.floor(this.getZ()))).is(BlockTags.RAILS)) {
                this.setPos(this.getX(), this.getY() - 2, this.getZ());
            }
        }
        if (nextState.getBlock() instanceof BaseRailBlock rail) {
            final RailShape shape = nextState.getValue(rail.getShapeProperty());
            if (shape == RailShape.NORTH_EAST || shape == RailShape.NORTH_WEST || shape == RailShape.SOUTH_EAST || shape == RailShape.SOUTH_WEST) {
                return currentMaxSpeed = VANILLA_MAX_SPEED;
            } else {
                final BlockState underState = minecart.level().getBlockState(currentPos.below());
                final ResourceLocation underBlockId = BuiltInRegistries.BLOCK.getKey(underState.getBlock());
                final Integer speedLimit = HighspeedService.getInstance().getSpeedLimit(underBlockId);
                if (speedLimit != null) {
                    return currentMaxSpeed = speedLimit / 20.0;
                } else {
                    return currentMaxSpeed = VANILLA_MAX_SPEED;
                }
            }
        } else {
            return currentMaxSpeed = VANILLA_MAX_SPEED;
        }
    }

    private void clampVelocity() {
        if (getModifiedMaxSpeed() != lastMaxSpeed) {
            double smaller = Math.min(getModifiedMaxSpeed(), lastMaxSpeed);
            final Vec3 vel = minecart.getDeltaMovement();
            minecart.setDeltaMovement(new Vec3(Mth.clamp(vel.x, -smaller, smaller), 0.0,
                    Mth.clamp(vel.z, -smaller, smaller)));
        }
        lastMaxSpeed = currentMaxSpeed;
    }
}

