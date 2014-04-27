package com.dgex.offspring.trader.askorders;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import nxt.Asset;
import nxt.Order;

import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.services.IStylingEngine;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import com.dgex.offspring.config.ContactsService;
import com.dgex.offspring.nxtCore.service.INxtService;
import com.dgex.offspring.swt.table.PaginationContainer;
import com.dgex.offspring.trader.api.IAssetExchange;
import com.dgex.offspring.ui.PlaceBidOrderWizard;
import com.dgex.offspring.user.service.IUserService;

public class AskOrdersPart {

  private Composite mainComposite;
  private AskOrdersViewer ordersViewer;
  private PaginationContainer paginationContainer;

  @PostConstruct
  public void postConstruct(final Composite parent, final INxtService nxt,
      final IUserService userService, IStylingEngine engine, UISynchronize sync) {
    mainComposite = new Composite(parent, SWT.NONE);
    GridLayoutFactory.fillDefaults().numColumns(1).spacing(5, 2).margins(0, 0)
        .applyTo(mainComposite);
    GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, true)
        .applyTo(mainComposite);

    paginationContainer = new PaginationContainer(mainComposite, SWT.NONE);
    GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, true)
        .applyTo(paginationContainer);

    ordersViewer = new AskOrdersViewer(paginationContainer.getViewerParent(),
        nxt, ContactsService.getInstance(), engine, userService, sync);
    ordersViewer.getTable().setToolTipText("Right click for Quick Buy");
    paginationContainer.setTableViewer(ordersViewer, 100);

    // add context menu for quick buy
    Menu contextMenu = new Menu(ordersViewer.getTable());
    ordersViewer.getTable().setMenu(contextMenu);
    MenuItem itemQuickBuy = new MenuItem(contextMenu, SWT.PUSH);
    itemQuickBuy.setText("Quick Buy");
    itemQuickBuy.addSelectionListener(new SelectionAdapter() {

      @Override
      public void widgetSelected(SelectionEvent e) {
        IStructuredSelection selection = (IStructuredSelection) ordersViewer
            .getSelection();
        Object order = selection.getFirstElement();
        if (order instanceof Order.Ask) {
          Shell shell = parent.getShell();
          Long assetId = ((Order.Ask) order).getAssetId();
          long quantityQNT = ((Order.Ask) order).getQuantityQNT();
          long priceNQT = ((Order.Ask) order).getPriceNQT();

          new WizardDialog(shell, new PlaceBidOrderWizard(userService, nxt,
              assetId, quantityQNT, priceNQT)).open();
        }
      }
    });

  }

  @Inject
  @Optional
  private void onAssetSelected(
      @UIEventTopic(IAssetExchange.TOPIC_ASSET_SELECTED) Asset asset) {
    if (ordersViewer != null && !ordersViewer.getControl().isDisposed()) {
      ordersViewer.setInput(asset.getId());
      ordersViewer.refresh();
    }
  }

}