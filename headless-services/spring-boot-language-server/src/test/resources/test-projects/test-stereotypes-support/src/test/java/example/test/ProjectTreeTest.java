package example.test;

import org.jmolecules.stereotype.catalog.support.CatalogSource;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.jmolecules.stereotype.reflection.ArchUnitStereotypeFactory;
import org.jmolecules.stereotype.reflection.ArchUnitStructureProvider;
import org.jmolecules.stereotype.reflection.ReflectionStereotypeFactory;
import org.jmolecules.stereotype.tooling.AsciiArtNodeHandler;
import org.jmolecules.stereotype.tooling.LabelUtils;
import org.jmolecules.stereotype.tooling.ProjectTree;
import org.jmolecules.stereotype.tooling.SpringLabelUtils;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import example.application.MainClass;

public class ProjectTreeTest {
	
	public static void main(String[] args) {
		new ProjectTreeTest().renderSpringBootProjectWithArchUnit();
	}

	void renderSpringBootProjectWithArchUnit() {

		var source = CatalogSource.ofClassLoader(ProjectTreeTest.class.getClassLoader());
		var catalog = new JsonPathStereotypeCatalog(source);
		var factory = new ReflectionStereotypeFactory(catalog);

		System.out.println(catalog);

		var structure = ArchUnitStructureProvider.asSinglePackage();
		var labelProvider = structure.getLabelProvider()
				.withTypeLabel(it -> LabelUtils.abbreviate(it.getName(), "example"))
				.withMethodLabel((m, t) -> SpringLabelUtils.requestMappings(m.reflect(), t == null ? null : t.reflect()));

		var handler = new AsciiArtNodeHandler<>(labelProvider);

		var tree = new ProjectTree<>(new ArchUnitStereotypeFactory(factory), catalog, handler)
				.withStructureProvider(structure)
				.withGrouper("org.jmolecules.architecture")
				.withGrouper("org.jmolecules.ddd", "org.jmolecules.event", "spring", "jpa", "java");

		var classes = new ClassFileImporter()
				.withImportOption(new ImportOption.DoNotIncludeTests())
				.importPackages("example");

		tree.process(classes.get(MainClass.class).getPackage());

		// System.out.println(jsonHandler.toString());
		System.out.println(handler.getWriter().toString());
	}

	
}
