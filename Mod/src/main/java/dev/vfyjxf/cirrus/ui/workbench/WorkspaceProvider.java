package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import org.jspecify.annotations.Nullable;

/**
 * Widget source for the four top-level workspaces; the shell only hosts and switches them.
 */
public interface WorkspaceProvider {

    Widget plannerWorkspace(WorkbenchShellContext ctx);

    @Nullable
    Widget facilitiesWorkspace(WorkbenchShellContext ctx);

    @Nullable
    Widget settingsWorkspace(WorkbenchShellContext ctx);

    Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx);
}
