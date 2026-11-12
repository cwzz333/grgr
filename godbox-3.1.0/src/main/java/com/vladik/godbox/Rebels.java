package com.vladik.godbox;

import com.vladik.godbox.Civ.City;
import com.vladik.godbox.Kingdoms.K;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class Rebels {
   private Rebels() {
   }

   static K free() {
      for (K k : K.values()) {
         if (!k.alive) {
            return k;
         }
      }

      return null;
   }

   static K owner(ServerWorld w, BlockPos p) {
      K best = null;
      double bd = 6400.0;

      for (K k : K.values()) {
         if (k.alive && k.world == w && k.capital != null) {
            double d = k.capital.getSquaredDistance(p);
            if (d < bd) {
               bd = d;
               best = k;
            }

            List<BlockPos> t = Conquest.TOWNS.get(k);
            if (t != null) {
               for (BlockPos q : t) {
                  double dq = q.getSquaredDistance(p);
                  if (dq < bd) {
                     bd = dq;
                     best = k;
                  }
               }
            }
         }
      }

      return best;
   }

   public static void inspire(ServerPlayerEntity god, ServerWorld w, BlockPos pos) {
      K old = owner(w, pos);
      if (old == null) {
         god.sendMessage(Text.literal("Рядом нет королевства, которое можно вдохновить на восстание").formatted(Formatting.GRAY), true);
      } else {
         if (!rebel(w, old, pos)) {
            god.sendMessage(Text.literal("Все королевства уже заняты — восстанию некуда стать").formatted(Formatting.GRAY), true);
         }
      }
   }

   static boolean rebel(ServerWorld w, K old, BlockPos at) {
      K nk = free();
      if (nk == null) {
         return false;
      } else {
         int era = Civ.city(old).era;
         int faith = Civ.city(old).religion;
         List<MobEntity> turn = new ArrayList<>();
         int limit = Math.max(1, old.members.size() / 2);

         for (MobEntity m : old.members) {
            if (turn.size() >= limit) {
               break;
            }

            if (m.isAlive() && m.getWorld() == w && m.getBlockPos().getSquaredDistance(at) < 784.0) {
               turn.add(m);
            }
         }

         Races.set(nk, Races.of(old));
         Kingdoms.found(null, nk.id, w, at, 2);
         if (!nk.alive) {
            return false;
         } else {
            for (MobEntity m : turn) {
               Kingdoms.join(nk, m);
            }

            City c = Civ.city(nk);
            c.era = era;
            c.religion = faith;
            List<BlockPos> t = Conquest.TOWNS.get(old);
            if (t != null) {
               t.removeIf(p -> p.getSquaredDistance(at) < 1600.0);
            }

            Diplomacy.set(old, nk, 1);
            Kingdoms.war = true;
            BlockPos b = nk.capital;
            Fx.particles(w, ParticleTypes.ANGRY_VILLAGER, (double)b.getX() + 0.5, (double)(b.getY() + 2), (double)b.getZ() + 0.5, 40, 4.0, 2.0, 4.0, 0.0);
            Fx.sound(w, (double)b.getX(), (double)b.getY(), (double)b.getZ(), SoundEvents.ENTITY_VILLAGER_NO, 3.0F, 0.7F);
            Chronicle.say(w.getServer(), nk, "восстало против: " + old.title + "! Началась гражданская война");
            return true;
         }
      }
   }

   public static void tick(MinecraftServer s, int cyc) {
      if (cyc % 300 == 0 && Events.on && !Kingdoms.eternalPeace) {
         for (K k : K.values()) {
            if (k.alive && k.world != null && k.capital != null) {
               List<BlockPos> t = Conquest.TOWNS.get(k);
               if (t != null && !t.isEmpty() && k.members.size() >= 40 && k.world.getRandom().nextInt(3) == 0) {
                  BlockPos far = t.get(0);

                  for (BlockPos p : t) {
                     if (p.getSquaredDistance(k.capital) > far.getSquaredDistance(k.capital)) {
                        far = p;
                     }
                  }

                  if (k.world.isChunkLoaded(far) && rebel(k.world, k, far)) {
                     return;
                  }
               }
            }
         }
      }
   }
}
