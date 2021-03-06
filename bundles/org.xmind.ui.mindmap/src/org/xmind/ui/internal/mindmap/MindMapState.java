package org.xmind.ui.internal.mindmap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.XMLMemento;
import org.xmind.core.ISheet;
import org.xmind.core.IWorkbook;
import org.xmind.gef.ui.editor.IGraphicalEditorPage;
import org.xmind.ui.internal.MindMapUIPlugin;
import org.xmind.ui.prefs.PrefConstants;
import org.xmind.ui.util.MindMapUtils;

public class MindMapState {

    private static final String TAG_STATES = "states"; //$NON-NLS-1$

    private static final String TAG_WORKBOOK = "workbook"; //$NON-NLS-1$

    private static final String TAG_SHEET = "sheet"; //$NON-NLS-1$

    private static final String TAG_STATE_ZOOM = "zoom"; //$NON-NLS-1$

    private static final String STATE_FILE = "/state.xml"; //$NON-NLS-1$

    private static final String ZOOM_VALUE = "zoom-value"; //$NON-NLS-1$

    private static final int NEGATIVE_INDEX = -1;

    private volatile static MindMapState instance;

    private File stateFile;

    private XMLMemento root;

    private MindMapState() {
        init();
    }

    private void init() {
        if (root == null) {
            root = getRootMemento();
        }
    }

    public void saveState(IGraphicalEditorPage[] pages) {
        if (pages != null && !(pages.length == 0)) {
            for (IGraphicalEditorPage page : pages)
                writeState(page);
        }
    }

    private void writeState(IGraphicalEditorPage page) {
        try {
            ISheet sheet = MindMapUtils.findSheet(page);
            IWorkbook workbook = sheet.getOwnedWorkbook();
            int workbookIndex = getWorkbookIndex(root.getChildren(TAG_WORKBOOK),
                    workbook.getFile());
            if (workbookIndex != NEGATIVE_INDEX) {
                IMemento workbookMem = root
                        .getChildren(TAG_WORKBOOK)[workbookIndex];
                int index = getSheetIndex(workbookMem.getChildren(TAG_SHEET),
                        sheet.getId());

                if (index != NEGATIVE_INDEX) {
                    IMemento sheetMem = workbookMem
                            .getChildren(TAG_SHEET)[index];
                    IMemento zoomMem = sheetMem.getChild(TAG_STATE_ZOOM);
                    setZoomValue(page, zoomMem);
                } else {
                    IMemento zoomMem = createZoomMem(sheet, workbookMem);
                    setZoomValue(page, zoomMem);
                }
            } else {
                IMemento workbookMem = root.createChild(TAG_WORKBOOK,
                        workbook.getFile());
                IMemento zoomMem = createZoomMem(sheet, workbookMem);
                setZoomValue(page, zoomMem);
            }

            File sf = getStateFile();
            Writer writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(sf), "utf-8"), //$NON-NLS-1$
                    1024);
            try {
                root.save(writer);
            } finally {
                writer.close();
            }
        } catch (Exception e) {
        }
    }

    private IMemento createZoomMem(ISheet sheet, IMemento workbookMem) {
        IMemento sheetMem = workbookMem.createChild(TAG_SHEET, sheet.getId());
        IMemento zoomMem = sheetMem.createChild(TAG_STATE_ZOOM);
        return zoomMem;
    }

    private void setZoomValue(IGraphicalEditorPage page, IMemento zoomMem) {
        zoomMem.putFloat(ZOOM_VALUE,
                (float) page.getViewer().getZoomManager().getScale());
    }

    public void loadState(IGraphicalEditorPage page) {
        ISheet sheet = MindMapUtils.findSheet(page);
        IWorkbook workbook = sheet.getOwnedWorkbook();

        boolean hasZoom = false;
        int workbookIndex = getWorkbookIndex(root.getChildren(),
                workbook.getFile());
        if (workbookIndex != NEGATIVE_INDEX) {
            IMemento workbookMem = root
                    .getChildren(TAG_WORKBOOK)[workbookIndex];
            int index = getSheetIndex(workbookMem.getChildren(TAG_SHEET),
                    sheet.getId());

            if (index != NEGATIVE_INDEX) {
                hasZoom = true;
                IMemento sheetMem = workbookMem.getChildren(TAG_SHEET)[index];
                IMemento zoomMem = sheetMem.getChild(TAG_STATE_ZOOM);
                Float zoom = zoomMem.getFloat(ZOOM_VALUE);
                page.getViewer().getZoomManager().setScale(zoom);
            }
        }

        if (!hasZoom) {
            int zoom = MindMapUIPlugin.getDefault().getPreferenceStore()
                    .getInt(PrefConstants.ZOOM_VALUE);
            if (zoom != 0) {
                page.getViewer().getZoomManager().setScale(zoom / 100d);
                hasZoom = true;
            }
        }

        if (!hasZoom) {
            int width = Display.getCurrent().getBounds().width;
            if (1366 <= width && width <= 1920)
                page.getViewer().getZoomManager().setScale(1.2d);
            else if (width > 1920)
                page.getViewer().getZoomManager().setScale(1.5d);
        }
    }

    private File getStateFile() {
        if (stateFile == null)
            stateFile = new File(
                    MindMapUIPlugin.getDefault().getStateLocation().toFile(),
                    STATE_FILE);
        return stateFile;
    }

    private int getSheetIndex(IMemento[] children, String id) {
        int index = 0;
        for (IMemento mem : children) {
            if (id.equals(mem.getID()))
                return index;
            index++;
        }
        return NEGATIVE_INDEX;
    }

    private int getWorkbookIndex(IMemento[] children, String file) {
        int index = 0;
        for (IMemento mem : children) {
            if (mem.getID().equals(file))
                return index;
            index++;
        }
        return NEGATIVE_INDEX;
    }

    private XMLMemento getRootMemento() {
        XMLMemento memento = null;
        InputStreamReader reader = null;
        try {
            File sf = getStateFile();
            reader = new InputStreamReader(new FileInputStream(sf), "utf-8"); //$NON-NLS-1$
            memento = XMLMemento.createReadRoot(reader);
        } catch (Exception e) {

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e2) {
                }
            }
        }

        if (memento == null) {
            memento = XMLMemento.createWriteRoot(TAG_STATES);
        }

        return memento;
    }

    public static MindMapState getInstance() {
        if (instance == null) {
            synchronized (MindMapState.class) {
                if (instance == null) {
                    instance = new MindMapState();
                }
            }
        }
        return instance;
    }

}
