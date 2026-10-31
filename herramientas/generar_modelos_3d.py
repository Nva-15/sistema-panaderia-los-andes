"""
Genera los modelos 3D (formato GLB) del catalogo de Realidad Aumentada de la Panaderia Los Andes.

Los modelos se arman con figuras geometricas (cilindros, esferas, conos) con materiales PBR,
en tamano real (metros) y con el origen en la base, para que en AR aparezcan apoyados sobre la mesa.

Uso:
    pip install trimesh numpy
    python herramientas/generar_modelos_3d.py

Salida: src/main/resources/static/modelos/*.glb
"""
import math
from pathlib import Path

import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial

SALIDA = Path(__file__).resolve().parent.parent / "src/main/resources/static/modelos"

# los cilindros y conos de trimesh se crean sobre el eje Z; glTF usa el eje Y hacia arriba
A_Y_ARRIBA = trimesh.transformations.rotation_matrix(-math.pi / 2, [1, 0, 0])


def lineal(componente):
    """glTF guarda el color base en espacio lineal: se convierte desde sRGB (el color que vemos en pantalla)."""
    return round(((componente / 255) ** 2.2) * 255)


def material(color, rugosidad=0.75, brillo=0.0):
    r, g, b = (lineal(c) for c in color)
    return PBRMaterial(baseColorFactor=[r, g, b, 255], roughnessFactor=rugosidad, metallicFactor=brillo)


def pintar(malla, color, rugosidad=0.75):
    malla.visual = trimesh.visual.TextureVisuals(material=material(color, rugosidad))
    return malla


def cilindro(radio, alto, y_base, color, secciones=64, rugosidad=0.75):
    m = trimesh.creation.cylinder(radius=radio, height=alto, sections=secciones)
    m.apply_transform(A_Y_ARRIBA)
    m.apply_translation([0, y_base + alto / 2, 0])
    return pintar(m, color, rugosidad)


def esfera(radio, centro, color, escala=(1, 1, 1), rugosidad=0.6, detalle=2):
    m = trimesh.creation.icosphere(subdivisions=detalle, radius=radio)
    m.apply_scale(escala)
    m.apply_translation(centro)
    return pintar(m, color, rugosidad)


def cono(radio, alto, y_base, color, centro_xz=(0, 0)):
    m = trimesh.creation.cone(radius=radio, height=alto, sections=32)
    m.apply_transform(A_Y_ARRIBA)
    m.apply_translation([centro_xz[0], y_base, centro_xz[1]])
    return pintar(m, color)


def anillo(cantidad, radio, y, crear):
    """Crea 'cantidad' piezas repartidas en circulo (decoraciones de las tortas)."""
    piezas = []
    for i in range(cantidad):
        ang = 2 * math.pi * i / cantidad
        piezas.append(crear((radio * math.cos(ang), y, radio * math.sin(ang))))
    return piezas


def guardar(nombre, piezas):
    escena = trimesh.Scene()
    for i, p in enumerate(piezas):
        escena.add_geometry(p, node_name=f"{nombre}_{i}")
    ruta = SALIDA / f"{nombre}.glb"
    ruta.write_bytes(escena.export(file_type="glb"))
    print(f"{ruta.name}: {ruta.stat().st_size / 1024:.0f} KB")


def torta(nombre, bizcocho, cobertura, crema, fruta):
    """Torta de dos pisos sobre base de carton, con crema y frutas (24 cm de diametro)."""
    piezas = [cilindro(0.15, 0.008, 0.0, (235, 235, 235), rugosidad=0.4)]               # base
    piezas.append(cilindro(0.12, 0.075, 0.008, bizcocho))                                # piso 1
    piezas.append(cilindro(0.122, 0.006, 0.083, cobertura, rugosidad=0.7))              # cobertura 1
    piezas += anillo(16, 0.112, 0.091, lambda c: esfera(0.011, c, crema, (1, 0.8, 1)))   # copos de crema
    piezas.append(cilindro(0.075, 0.055, 0.089, bizcocho))                               # piso 2
    piezas.append(cilindro(0.077, 0.006, 0.144, cobertura, rugosidad=0.7))              # cobertura 2
    piezas += anillo(8, 0.055, 0.158, lambda c: esfera(0.012, c, fruta, (1, 1.1, 1), 0.3))  # frutas
    piezas += anillo(10, 0.123, 0.03, lambda c: esfera(0.009, c, crema, (1, 1.4, 1)))  # borde inferior
    guardar(nombre, piezas)


def pan_frances():
    """Pan frances peruano: bollo dorado con corte al medio (10 cm)."""
    dorado, corte = (190, 120, 50), (235, 200, 140)
    piezas = [
        esfera(0.05, (-0.018, 0.03, 0), dorado, (0.75, 0.6, 0.9), 0.85, detalle=4),
        esfera(0.05, (0.018, 0.03, 0), dorado, (0.75, 0.6, 0.9), 0.85, detalle=4),
        esfera(0.04, (0, 0.036, 0), corte, (0.25, 0.55, 0.95), 0.9, detalle=4),
    ]
    guardar("pan_frances", piezas)


def pastel():
    """Pastel tipo cupcake: capacillo de papel y crema decorada (8 cm)."""
    capacillo = trimesh.creation.cylinder(radius=0.03, height=0.04, sections=24)
    # se ensancha la parte superior para formar el capacillo
    v = capacillo.vertices
    v[:, 0] *= 1 + (v[:, 2] + 0.02) * 5
    v[:, 1] *= 1 + (v[:, 2] + 0.02) * 5
    capacillo.vertices = v
    capacillo.apply_transform(A_Y_ARRIBA)
    capacillo.apply_translation([0, 0.02, 0])
    piezas = [
        pintar(capacillo, (205, 60, 90), 0.6),
        esfera(0.036, (0, 0.045, 0), (150, 95, 55), (1, 0.45, 1), 0.85),     # bizcocho
        cono(0.034, 0.05, 0.05, (255, 225, 235)),                           # crema
        esfera(0.008, (0, 0.102, 0), (190, 20, 40), rugosidad=0.25),         # cereza
    ]
    guardar("pastel", piezas)


if __name__ == "__main__":
    SALIDA.mkdir(parents=True, exist_ok=True)
    torta("torta_chocolate", bizcocho=(95, 55, 35), cobertura=(70, 38, 22), crema=(245, 235, 220), fruta=(180, 20, 35))
    torta("torta_fresa", bizcocho=(250, 205, 215), cobertura=(240, 150, 175), crema=(255, 250, 250), fruta=(210, 30, 50))
    torta("torta_tres_leches", bizcocho=(250, 235, 200), cobertura=(255, 250, 240), crema=(255, 255, 255), fruta=(230, 180, 60))
    pan_frances()
    pastel()
