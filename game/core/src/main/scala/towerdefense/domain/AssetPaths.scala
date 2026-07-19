package towerdefense.domain

// One representative image per building/unit/resource, relative to `game/assets/` — the
// same underlying files the browser app's own (richer, animation-frame-aware)
// js.main.GameApp.AssetPaths loads, just the single "reference" frame each already uses
// for a still image (a building's static sprite, or a directional/animated unit's first
// front-facing frame). This is what the doc generator embeds in each page, so a vault
// image and the in-game sprite can never point at two different files for the same kind.
// Resource entries are the vault's own hand-drawn "-reference.png" art (Shadow has none —
// Resources/Mort/Ombre.md has always shipped without an image, and there's no
// ombre-reference.png in assets/ to give it one).
object AssetPaths:
  def building(kind: BuildingKind): String = kind match
    case BuildingKind.Grove           => "grove.png"
    case BuildingKind.Forest          => "forest.png"
    case BuildingKind.Jungle          => "jungle.png"
    case BuildingKind.Stonehenge      => "stonehenge.png"
    case BuildingKind.Cave            => "cave.png"
    case BuildingKind.Labyrinth       => "labyrinthe.png"
    case BuildingKind.Church          => "eglise.png"
    case BuildingKind.Watchtower      => "watchtower.png"
    case BuildingKind.Angel           => "angel.png"
    case BuildingKind.Tomb            => "tomb.png"
    case BuildingKind.BlackCastle     => "chateau-noir.png"
    case BuildingKind.DeathHouse      => "death-house.png"
    case BuildingKind.PassingGate     => "passing-gate.png"
    case BuildingKind.LaboFondamental => "labo-fondamental.png"
    case BuildingKind.LaboNaturel     => "labo-naturel.png"
    case BuildingKind.LaboSombre      => "labo-sombre.png"
    case BuildingKind.LaboDeRecherche => "labo-de-recherche.png"
    case BuildingKind.LaboDeLaLoi     => "labo-de-la-loi.png"
    case BuildingKind.LaboDuChaos     => "labo-du-chaos.png"

  def unit(kind: UnitKind): String = kind match
    case UnitKind.Elf         => "elf/front-walk-00.png"
    case UnitKind.Goblin      => "goblin/front-walk-00.png"
    case UnitKind.Minotaur    => "minotaur.png"
    case UnitKind.Paladin     => "paladin.png"
    case UnitKind.Wolf        => "wolf-reference.png"
    case UnitKind.Zombie      => "zombie/walk-00.png"
    case UnitKind.Vampire     => "vampire.png"
    case UnitKind.Necromancer => "necromancer/walk-00.png"
    case UnitKind.Soul        => "soul/walk-00.png"
    case UnitKind.Tree        => "tree/front-walk-00.png"

  def resource(res: Resource): Option[String] = res match
    case Resource.Wood    => Some("bois-reference.png")
    case Resource.Fire    => Some("feu-reference.png")
    case Resource.Light   => Some("lumiere-reference.png")
    case Resource.Shadow  => None
    case Resource.Crystal => Some("crystal-reference.png")
