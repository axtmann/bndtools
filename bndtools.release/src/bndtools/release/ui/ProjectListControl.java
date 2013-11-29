/*******************************************************************************
 * Copyright (c) 2012 Per Kr. Soreide.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Per Kr. Soreide - initial API and implementation
 *******************************************************************************/
package bndtools.release.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bndtools.utils.swt.FilterPanelPart;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import aQute.libg.glob.Glob;
import bndtools.release.Activator;
import bndtools.release.ProjectDiff;
import bndtools.release.nl.Messages;
import bndtools.release.ui.TableSortingEnabler.IColumnContentProvider;

public class ProjectListControl {

    private Color COLOR_RELEASE_REQUIRED;
    private Color COLOR_VERSION_UPDATE_REQUIRED;

	private Table projects;
	private String[] releaseRepos;
	private CheckboxTableViewer tableViewer;
	private final SelectionListener selectionListener;
	private List<ProjectDiff> projectDiffs;
	private ContentProvider contentProvider;

    private static final String PROJECT_COLUMN = "project";
    private static final String REPOSITORY_COLUMN = "repository";
    private static final String BUNDLES_COLUMN = "bundles";

    // Set column names
    private static final String[] columnNames = new String[] {
            PROJECT_COLUMN,
            REPOSITORY_COLUMN,
            BUNDLES_COLUMN
            };

	public ProjectListControl(SelectionListener selectionListener, String[] releaseRepos) {
		this.selectionListener = selectionListener;
		this.releaseRepos = releaseRepos;

		COLOR_VERSION_UPDATE_REQUIRED = new Color(Display.getCurrent(), 250, 85, 125);
        COLOR_RELEASE_REQUIRED = new Color(Display.getCurrent(), 100, 250, 100);
	}

    public void createControl(final Composite parent) {

        createFilter(parent);

        // Create the composite
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(1, false));
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createTableLayout(composite);
        createLegend(composite);
    }

    private void createFilter(Composite parent) {

        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 0;

        composite.setLayout(gridLayout);
        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.grabExcessHorizontalSpace = true;
        composite.setLayoutData(gridData);

        FilterPanelPart filterPart = new FilterPanelPart(Activator.getDefault().getScheduler());
        filterPart.createControl(composite, 0, 0);
        filterPart.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                String filter = (String) event.getNewValue();
                updatedFilter(filter);
            }
        });

        ToolBar toolbar = new ToolBar(composite, SWT.FLAT);
        ToolItem tiCheckAll = new ToolItem(toolbar, SWT.FLAT);
        tiCheckAll.setImage(Activator.getImageDescriptor("icons/check_all.gif").createImage());
        tiCheckAll.setToolTipText(Messages.checkAll);
        tiCheckAll.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                Object[] objs = contentProvider.getElements(null);
                for (Object obj : objs) {
                    ProjectDiff diff = (ProjectDiff) obj;
                    diff.setRelease(true);
                }
                tableViewer.refresh();
            }
        });

        ToolItem tiUncheckAll = new ToolItem(toolbar, SWT.FLAT);
        tiUncheckAll.setImage(Activator.getImageDescriptor("icons/uncheck_all.gif").createImage());
        tiUncheckAll.setToolTipText(Messages.uncheckAll);
        tiUncheckAll.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                Object[] objs = contentProvider.getElements(null);
                for (Object obj : objs) {
                    ProjectDiff diff = (ProjectDiff) obj;
                    diff.setRelease(false);
                }
                tableViewer.refresh();
            }
        });
    }

    private void updatedFilter(String filterString) {
        String newFilter;
        if (filterString == null || filterString.length() == 0 || filterString.trim().equals("*"))
            newFilter = null;
        else
            newFilter = "*" + filterString.trim() + "*";
        contentProvider.setFilter(newFilter);
        tableViewer.refresh();
    }

    private void createTableLayout(Composite parent) {

        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Add TableColumnLayout
        TableColumnLayout layout = new TableColumnLayout();
        composite.setLayout(layout);

        // Instantiate TableViewer
        projects = new Table(composite, SWT.CHECK | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        projects.setHeaderVisible(true);
        projects.setLinesVisible(true);
        projects.addSelectionListener(new SelectionListener() {
            public void widgetSelected(SelectionEvent e) {
                selectionListener.widgetSelected(e);
            }
            public void widgetDefaultSelected(SelectionEvent e) {
                selectionListener.widgetDefaultSelected(e);
            }
        });
        tableViewer = new CheckboxTableViewer(projects);
        tableViewer.setUseHashlookup(true);

        // Project
        TableViewerColumn tableViewerColumn = new TableViewerColumn(tableViewer, SWT.LEFT);
        TableColumn tableCol = tableViewerColumn.getColumn();
        layout.setColumnData(tableCol, new ColumnWeightData(20, 100, true));
        tableCol.setText(Messages.project1);

        // Repository
        tableViewerColumn = new TableViewerColumn(tableViewer, SWT.LEFT);
        tableCol = tableViewerColumn.getColumn();
        layout.setColumnData(tableCol, new ColumnWeightData(15, 80, true));
        tableCol.setText(Messages.repository);

        // Bundles
        tableViewerColumn = new TableViewerColumn(tableViewer, SWT.CENTER);
        tableCol = tableViewerColumn.getColumn();
        layout.setColumnData(tableCol, new ColumnPixelData(35, true, true));
        tableCol.setText(Messages.bundles);

        // Create the cell editors
        CellEditor[] editors = new CellEditor[columnNames.length];

        // Column 1 : Project
        TextCellEditor textEditor = new TextCellEditor(projects);
        ((Text) textEditor.getControl()).setEditable(false);
        editors[0] = textEditor;

        // Column 2 : Repository (Combo Box)
        editors[1] = new ComboBoxCellEditor(projects, releaseRepos, SWT.READ_ONLY);

        // Column 3 : Number of bundles
        textEditor = new TextCellEditor(projects);
        ((Text) textEditor.getControl()).setEditable(false);
        editors[2] = textEditor;

        // Assign the cell editors to the viewer
        tableViewer.setCellEditors(editors);

        contentProvider = new ContentProvider();
        tableViewer.setCellModifier(new TableCellModifier());
        tableViewer.setContentProvider(contentProvider);
        tableViewer.setLabelProvider(new TableLabelProvider());
        tableViewer.setColumnProperties(columnNames);
        tableViewer.setCheckStateProvider(new ICheckStateProvider() {

            public boolean isGrayed(Object element) {
                return false;
            }

            public boolean isChecked(Object element) {
                ProjectDiff diff = (ProjectDiff) element;
                return diff.isRelease();
            }
        });

        TableSortingEnabler.applyTableColumnSorting(tableViewer);
    }

    private void createLegend(Composite parent) {

        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.verticalSpacing = 2;
        gridLayout.marginHeight = 2;
        gridLayout.marginWidth = 0;
        gridLayout.horizontalSpacing = 2;
        composite.setLayout(gridLayout);

        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.grabExcessHorizontalSpace = true;
        composite.setLayoutData(gridData);

        Text t = new Text(composite, SWT.BORDER | SWT.MULTI);
        t.setSize(5, 5);
        t.setBackground(COLOR_VERSION_UPDATE_REQUIRED);
        t.setEditable(false);
        t.setText(" ");

        Label l = new Label(composite, SWT.NONE);
        l.setText(Messages.versionUpdateRequired);

        t = new Text(composite, SWT.BORDER | SWT.MULTI);
        t.setSize(5, 5);
        t.setBackground(COLOR_RELEASE_REQUIRED);
        t.setEditable(false);
        t.setText(" ");

        l = new Label(composite, SWT.NONE);
        l.setText(Messages.releaseRequired);
    }

    public void setInput(List<ProjectDiff> projectDiffs) {
        this.projectDiffs = projectDiffs;
        tableViewer.setInput(projectDiffs);
    }

	public Table getTable() {
		return projects;
	}

	public void setSelected(int index) {
		projects.select(index);
	}

	private class ContentProvider implements IStructuredContentProvider, IColumnContentProvider {

	    private AtomicReference<String> filterRef = new AtomicReference<String>();

	    public void setFilter(String filter) {
	        this.filterRef.set(filter);
	    }

        public void dispose() {
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        public Object[] getElements(Object parent) {
            final String filter = filterRef.get();
            if (filter == null || "".equals(filter))
                return projectDiffs.toArray();

            Glob glob = new Glob(filter);
            List<ProjectDiff> filtered = new ArrayList<ProjectDiff>();
            for (ProjectDiff diff : projectDiffs) {
                if (glob.matcher(diff.getProject().getName()).matches()) {
                    filtered.add(diff);
                }
            }
            return filtered.toArray();
        }

        public Comparable<?> getValue(Object element, int columnIndex) {
            ProjectDiff diff = (ProjectDiff) element;
            switch (columnIndex) {
            case 0:
                return diff.getProject().getName();
            case 1:
                return diff.getReleaseRepository();
            case 2:
                int bundles = -1;
                try {
                    bundles = diff.getProject().getSubBuilders().size();
                } catch (Exception e) {
                    /* ignore */
                }
                return new Integer(bundles);
            }
            return "";
        }
	}

	private class TableLabelProvider extends LabelProvider implements ITableColorProvider, ITableLabelProvider {

        public Image getColumnImage(Object element, int columnIndex) {
            return null;
        }

        public String getColumnText(Object element, int columnIndex) {
            String text = "";
            ProjectDiff diff = (ProjectDiff) element;
            switch (columnIndex) {
            case 0:
                text = diff.getProject().getName();
                break;
            case 1:
                text = diff.getReleaseRepository();
                if (text == null) {
                    text = diff.getDefaultReleaseRepository();
                }
                break;
            case 2:
              int bundles = -1;
              try {
                  bundles = diff.getProject().getSubBuilders().size();
              } catch (Exception e) {
                  /* ignore */
              }
              text = String.valueOf(bundles);
              break;
            default:
                break;
            }
            return text;
        }

        public Color getBackground(Object element, int columnIndex) {
            ProjectDiff diff = (ProjectDiff) element;
            if (diff.isVersionUpdateRequired()) {
                return COLOR_VERSION_UPDATE_REQUIRED;
            }
            if (diff.isReleaseRequired()) {
                return COLOR_RELEASE_REQUIRED;
            }
            return null;
        }

        public Color getForeground(Object element, int columnIndex) {
            return null;
        }
	}

	private class TableCellModifier implements ICellModifier {

	    private List<String> names = Arrays.asList(columnNames);

        public boolean canModify(Object element, String property) {
            // Find the index of the column
            int columnIndex = names.indexOf(property);
            if (columnIndex == 1)
                return true;
            return false;
        }

        public Object getValue(Object element, String property) {
            int columnIndex = names.indexOf(property);
            Object result = null;
            ProjectDiff diff = (ProjectDiff) element;
            switch (columnIndex) {
            case 0:
                result = diff.getProject().getName();
                break;
            case 1:
                String stringValue = diff.getReleaseRepository();
                if (stringValue == null)
                    stringValue = diff.getDefaultReleaseRepository();
                int i = releaseRepos.length - 1;
                while (!stringValue.equals(releaseRepos[i]) && i > 0)
                    --i;
                result = new Integer(i);
                break;
            case 2:
              int bundles = -1;
              try {
                  bundles = diff.getProject().getSubBuilders().size();
              } catch (Exception e) {
                  /* ignore */
              }
              result = String.valueOf(bundles);
              break;
            default:
                break;
            }
            return result;
        }

        public void modify(Object element, String property, Object value) {
            // Find the index of the column
            int columnIndex = names.indexOf(property);

            TableItem item = (TableItem) element;
            ProjectDiff diff = (ProjectDiff) item.getData();
            String valueString;

            switch (columnIndex) {
                case 1 : // Repository
                    if (((Integer) value).intValue() < 0)
                        break;
                    valueString = releaseRepos[((Integer) value).intValue()];
                    if (diff.getReleaseRepository() == null || !diff.getReleaseRepository().equals(valueString)) {
                        diff.setReleaseRepository(valueString);
                    }
                    item.setText(1, valueString);
                    break;
                default :
                }
        }
	}

	public void dispose() {
	    COLOR_RELEASE_REQUIRED.dispose();
	    COLOR_VERSION_UPDATE_REQUIRED.dispose();
	}
}