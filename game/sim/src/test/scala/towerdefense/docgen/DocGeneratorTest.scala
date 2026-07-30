package towerdefense.docgen

import java.nio.file.{Files, Path}
import towerdefense.domain.*
import towerdefense.domain.i18n.*

class DocGeneratorTest extends munit.FunSuite:

  private def generatedTo(tmp: Path): Unit = DocGenerator.generate(tmp.toString)

  private def read(tmp: Path, relative: String): String =
    Files.readString(tmp.resolve(relative))

  test("a chain-upgrade building's page states its tier, produces rate, and upgrade from/to") {
    val tmp = Files.createTempDirectory("docgen-test")
    generatedTo(tmp)
    val forest = read(
      tmp,
      EntityNames.vaultRoot(Lang.Fr) + "/" + EntityNames.buildingPath(BuildingKind.Forest, Lang.Fr)
    )
    assert(forest.contains("tier: 2"), forest)
    assert(forest.contains("produit en bois: 0.5"), forest)
    assert(forest.contains("amelioration de:"), forest)
    assert(forest.contains("ameliore vers:"), forest)
    assert(forest.contains("degats par seconde: 2"), forest)
  }

  test("LaboFondamental's upgrade-to list has all 5 specific labs") {
    val tmp = Files.createTempDirectory("docgen-test")
    generatedTo(tmp)
    val fondamental =
      read(
        tmp,
        EntityNames.vaultRoot(Lang.Fr) + "/" + EntityNames.buildingPath(
          BuildingKind.LaboFondamental,
          Lang.Fr
        )
      )
    List("Labo Naturel", "Labo Sombre", "Labo de Recherche", "Labo de la Loi", "Labo du Chaos")
      .foreach { name =>
        assert(fondamental.contains(name), s"expected $name in:\n$fondamental")
      }
  }

  test("a unit's page states its tier, plunder amount, spawning building link, and speed") {
    val tmp = Files.createTempDirectory("docgen-test")
    generatedTo(tmp)
    val goblin = read(
      tmp,
      EntityNames.vaultRoot(Lang.En) + "/" + EntityNames.unitPath(UnitKind.Goblin, Lang.En)
    )
    assert(goblin.contains("tier: 1"), goblin)
    assert(goblin.contains("plunder in gold: 2"), goblin)
    assert(goblin.contains("produced by:"), goblin)
    assert(goblin.contains("Cave"), goblin)
    assert(goblin.contains("speed (cells/sec): 1"), goblin)
  }

  test("a building with no combat ability never gets a dps line") {
    val tmp = Files.createTempDirectory("docgen-test")
    generatedTo(tmp)
    val cave = read(
      tmp,
      EntityNames.vaultRoot(Lang.Fr) + "/" + EntityNames.buildingPath(BuildingKind.Cave, Lang.Fr)
    )
    assert(!cave.contains("degats par seconde"), cave)
  }

  test("a resource with a faction (Crystal) gets its own page, with a faction link") {
    val tmp = Files.createTempDirectory("docgen-test")
    generatedTo(tmp)
    val faction = EntityNames.resourceInfo(Resource.Crystal).faction.get
    val crystal =
      read(
        tmp,
        EntityNames
          .vaultRoot(Lang.Fr) + "/" + EntityNames.resourcePath(Resource.Crystal, faction, Lang.Fr)
      )
    assert(crystal.contains("type: ressource"), crystal)
    assert(crystal.contains(EntityNames.factionName(faction, Lang.Fr)), crystal)
  }
