import { Suspense, useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { ContactShadows, Environment, Float, Lightformer, RoundedBox, Sparkles, useTexture } from '@react-three/drei';
import * as THREE from 'three';
import type { Group } from 'three';
import homeImage from '../assets/home.png';

const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

type Crop = { x: number; y: number; w: number; h: number };

// Pixel regions of the 1220x2712 home screenshot used as scene content.
const FULL_SCREEN: Crop = { x: 0, y: 0, w: 1220, h: 2712 };
const RECENT_YOUTUBE: Crop = { x: 71, y: 1625, w: 365, h: 422 };
const RECENT_GITHUB: Crop = { x: 491, y: 1625, w: 360, h: 443 };
const STATS_BLOCKED: Crop = { x: 100, y: 725, w: 490, h: 190 };
const FAVORITE_GITHUB: Crop = { x: 655, y: 1180, w: 250, h: 165 };

// Crops the screenshot into a rounded, screen-like texture.
function useCropTexture(image: HTMLImageElement, crop: Crop, outW: number, outH: number, radius: number) {
  const texture = useMemo(() => {
    const canvas = document.createElement('canvas');
    canvas.width = outW;
    canvas.height = outH;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;

    const scale = Math.max(outW / crop.w, outH / crop.h);
    const drawW = crop.w * scale;
    const drawH = crop.h * scale;
    ctx.save();
    ctx.beginPath();
    ctx.roundRect(0, 0, outW, outH, radius);
    ctx.clip();
    ctx.drawImage(image, crop.x, crop.y, crop.w, crop.h, (outW - drawW) / 2, (outH - drawH) / 2, drawW, drawH);
    ctx.restore();

    const result = new THREE.CanvasTexture(canvas);
    result.colorSpace = THREE.SRGBColorSpace;
    result.anisotropy = 8;
    return result;
  }, [image, crop, outW, outH, radius]);

  useEffect(() => () => texture?.dispose(), [texture]);
  return texture;
}

function PhoneScreen() {
  const { image } = useTexture(homeImage);
  const texture = useCropTexture(image as HTMLImageElement, FULL_SCREEN, 640, 1424, 58);
  if (!texture) return null;
  return (
    <mesh position={[0, 0, 0.19]}>
      <planeGeometry args={[1.88, 4.17]} />
      <meshBasicMaterial map={texture} toneMapped={false} transparent />
    </mesh>
  );
}

function PhotoCard({
  image,
  crop,
  position,
  rotation,
  width,
  height,
  depth = 0.09,
  radius = 0.11,
}: {
  image: HTMLImageElement;
  crop: Crop;
  position: [number, number, number];
  rotation: [number, number, number];
  width: number;
  height: number;
  depth?: number;
  radius?: number;
}) {
  const planeW = width - 0.1;
  const planeH = height - 0.1;
  const texture = useCropTexture(image, crop, Math.round(planeW * 300), Math.round(planeH * 300), Math.round(radius * 300));
  if (!texture) return null;
  return (
    <group position={position} rotation={rotation}>
      <RoundedBox args={[width, height, depth]} radius={radius} smoothness={4}>
        <meshPhysicalMaterial color="#121916" metalness={0.4} roughness={0.28} clearcoat={0.7} />
      </RoundedBox>
      <mesh position={[0, 0, depth / 2 + 0.002]}>
        <planeGeometry args={[planeW, planeH]} />
        <meshBasicMaterial map={texture} toneMapped={false} transparent />
      </mesh>
    </group>
  );
}

function BrowserPhone() {
  const groupRef = useRef<Group>(null);
  const haloRef = useRef<Group>(null);

  useFrame((state, delta) => {
    const group = groupRef.current;
    if (group) {
      group.rotation.y = THREE.MathUtils.damp(group.rotation.y, -0.3 + state.pointer.x * 0.22, 3.2, delta);
      group.rotation.x = THREE.MathUtils.damp(group.rotation.x, 0.07 - state.pointer.y * 0.13, 3.2, delta);
      group.position.y = Math.sin(state.clock.elapsedTime * 0.72) * 0.06 - 0.12;
    }

    if (haloRef.current) {
      haloRef.current.rotation.z = Math.sin(state.clock.elapsedTime * 0.2) * 0.08;
      haloRef.current.rotation.x = Math.cos(state.clock.elapsedTime * 0.16) * 0.04;
    }
  });

  return (
    <group ref={groupRef} position={[0, -0.12, 0]} rotation={[0.07, -0.3, 0]} scale={0.94}>
      <group ref={haloRef} position={[0, 0, -0.8]}>
        <mesh>
          <torusGeometry args={[1.92, 0.012, 12, 120]} />
          <meshBasicMaterial color="#56DBCF" transparent opacity={0.34} />
        </mesh>
        <mesh rotation={[0, Math.PI / 3, 0]}>
          <torusGeometry args={[2.13, 0.006, 12, 120]} />
          <meshBasicMaterial color="#A5F2E9" transparent opacity={0.24} />
        </mesh>
      </group>

      <RoundedBox args={[2.26, 4.5, 0.3]} radius={0.29} smoothness={6}>
        <meshPhysicalMaterial
          color="#111714"
          metalness={0.78}
          roughness={0.24}
          clearcoat={0.95}
          clearcoatRoughness={0.22}
          reflectivity={0.55}
        />
      </RoundedBox>

      <RoundedBox position={[0, 0, 0.16]} args={[2.02, 4.26, 0.035]} radius={0.22} smoothness={5}>
        <meshStandardMaterial color="#070A09" emissive="#0A120F" emissiveIntensity={0.55} roughness={0.28} />
      </RoundedBox>

      <Suspense fallback={null}>
        <PhoneScreen />
      </Suspense>

      <mesh position={[1.14, 0.58, 0.02]}>
        <boxGeometry args={[0.035, 0.62, 0.1]} />
        <meshStandardMaterial color="#2D3733" metalness={0.8} roughness={0.3} />
      </mesh>
      <mesh position={[-1.14, 0.25, 0.02]}>
        <boxGeometry args={[0.035, 0.42, 0.1]} />
        <meshStandardMaterial color="#2D3733" metalness={0.8} roughness={0.3} />
      </mesh>

      <pointLight position={[0, -1.7, 1.1]} color="#56DBCF" intensity={2.6} distance={4} />
      <pointLight position={[0.8, 0.4, 0.9]} color="#A5F2E9" intensity={1.2} distance={3.4} />
    </group>
  );
}

function PhotoCards() {
  const { image } = useTexture(homeImage);
  const source = image as HTMLImageElement;
  return (
    <>
      <Float speed={1.2} rotationIntensity={0.28} floatIntensity={0.65}>
        <PhotoCard
          image={source}
          crop={RECENT_YOUTUBE}
          position={[-1.85, 1.08, 0.5]}
          rotation={[0.04, 0.24, 0.08]}
          width={1.12}
          height={1.28}
        />
      </Float>
      <Float speed={1.55} rotationIntensity={0.22} floatIntensity={0.72}>
        <PhotoCard
          image={source}
          crop={RECENT_GITHUB}
          position={[1.82, -0.44, 0.58]}
          rotation={[0.02, -0.28, -0.08]}
          width={1.04}
          height={1.28}
        />
      </Float>
      <Float speed={1.75} rotationIntensity={0.3} floatIntensity={0.55}>
        <PhotoCard
          image={source}
          crop={STATS_BLOCKED}
          position={[1.72, 1.62, 0.28]}
          rotation={[0.05, -0.2, -0.06]}
          width={0.92}
          height={0.42}
          depth={0.08}
          radius={0.09}
        />
      </Float>
      <Float speed={1.4} rotationIntensity={0.35} floatIntensity={0.5}>
        <PhotoCard
          image={source}
          crop={FAVORITE_GITHUB}
          position={[-1.65, -1.42, 0.62]}
          rotation={[0.02, 0.2, 0.12]}
          width={0.74}
          height={0.52}
          depth={0.08}
          radius={0.13}
        />
      </Float>
    </>
  );
}

function Scene() {
  return (
    <>
      <ambientLight intensity={0.7} />
      <directionalLight position={[4, 6, 6]} intensity={2.4} color="#fff8e8" />
      <directionalLight position={[-5, -1, 3]} intensity={0.8} color="#56DBCF" />
      <pointLight position={[-4, 2, 4]} intensity={18} distance={12} color="#56DBCF" />
      <pointLight position={[4, -2, 4]} intensity={12} distance={12} color="#A5F2E9" />

      <BrowserPhone />

      <Suspense fallback={null}>
        <PhotoCards />
      </Suspense>

      <Float speed={2} rotationIntensity={0.6} floatIntensity={0.4}>
        <mesh position={[-1.95, 2.25, 0.1]} rotation={[0.4, 0.2, 0.3]}>
          <torusKnotGeometry args={[0.16, 0.045, 64, 8]} />
          <meshStandardMaterial color="#56DBCF" emissive="#56DBCF" emissiveIntensity={0.35} metalness={0.7} roughness={0.2} />
        </mesh>
      </Float>

      <Sparkles count={isMobile ? 26 : 54} scale={[5.5, 6.4, 3.2]} size={1.3} speed={0.22} opacity={0.55} color="#56DBCF" />
      <Sparkles count={isMobile ? 14 : 26} scale={[5, 6, 2.4]} size={0.9} speed={0.16} opacity={0.42} color="#A5F2E9" />

      <ContactShadows position={[0, -2.6, 0]} opacity={0.5} scale={8} blur={2.8} far={5.5} color="#000000" resolution={256} frames={1} />

      <Environment resolution={64}>
        <Lightformer form="rect" intensity={4.5} color="#B8F0E8" position={[0, 4, 4]} scale={[8, 4, 1]} />
        <Lightformer form="rect" intensity={2.6} color="#A5F2E9" position={[-4, 1, 3]} scale={[3, 6, 1]} rotation={[0, Math.PI / 4, 0]} />
        <Lightformer form="rect" intensity={1.8} color="#7FD8CB" position={[4, -1, 2]} scale={[3, 5, 1]} rotation={[0, -Math.PI / 4, 0]} />
        <Lightformer form="circle" intensity={2.2} color="#ffffff" position={[0, 5, -4]} scale={[4, 4, 1]} />
      </Environment>
    </>
  );
}

export default function BrowserScene() {
  return (
    <Canvas
      dpr={[1, isMobile ? 1.4 : 1.75]}
      camera={{ position: [0, 0.1, 8.6], fov: 36, near: 0.1, far: 30 }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      onCreated={({ gl }) => {
        gl.toneMapping = THREE.ACESFilmicToneMapping;
        gl.toneMappingExposure = 1.04;
      }}
    >
      <Suspense fallback={null}>
        <Scene />
      </Suspense>
    </Canvas>
  );
}
