package com.dgex.offspring.trader.mybuyorders;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import nxt.Account;
import nxt.Asset;
import nxt.Attachment;
import nxt.Nxt;
import nxt.Order;
import nxt.Transaction;
import nxt.TransactionType;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.dgex.offspring.config.CompareMe;
import com.dgex.offspring.nxtCore.service.INxtService;
import com.dgex.offspring.swt.table.GenerericTableViewer;
import com.dgex.offspring.swt.table.GenericComparator;
import com.dgex.offspring.swt.table.GenericTableColumnBuilder;
import com.dgex.offspring.swt.table.ICellDataProvider;
import com.dgex.offspring.swt.table.IGenericTable;
import com.dgex.offspring.swt.table.IGenericTableColumn;
import com.dgex.offspring.trader.api.IAssetExchange;
import com.dgex.offspring.user.service.IUser;
import com.dgex.offspring.user.service.IUserService;

public class MyBuyOrdersViewer extends GenerericTableViewer {

  static String EXTENT_COLUMN_PENDING = "##";
  static String EXTENT_COLUMN_TOTAL = "000000";
  static String EXTENT_COLUMN_PRICE = "000000";
  static String EXTENT_COLUMN_QUANTITY = "000000";

  static DecimalFormat formatDouble = new DecimalFormat("#.##");

  final IGenericTableColumn columnPending = new GenericTableColumnBuilder("#")
      .align(SWT.CENTER).textExtent("##").provider(new ICellDataProvider() {

        @Override
        public Object getCellValue(Object element) {
          OrderWrapper order = (OrderWrapper) element;
          return Boolean.valueOf(Order.Bid.getBidOrder(order.getId()) == null);
        }

        @Override
        public void getCellData(Object element, Object[] data) {
          data[FONT] = JFaceResources.getFontRegistry().getBold("");
          data[ICellDataProvider.TEXT] = (Boolean) getCellValue(element) ? "-"
              : "+";
        }

        @Override
        public int compare(Object v1, Object v2) {
          return CompareMe.compare((Boolean) v1, (Boolean) v2);
        }
      }).build();

  final IGenericTableColumn columnTotal = new GenericTableColumnBuilder("Total")
      .align(SWT.RIGHT).textExtent(EXTENT_COLUMN_TOTAL)
      .provider(new ICellDataProvider() {

        @Override
        public Object getCellValue(Object element) {
          OrderWrapper order = (OrderWrapper) element;
          Double price = Double.valueOf((Long.valueOf(order.getPrice())
              .doubleValue() / 100));
          return Double.valueOf(price * order.getQuantity());
        }

        @Override
        public void getCellData(Object element, Object[] data) {
          data[ICellDataProvider.TEXT] = formatDouble
              .format(getCellValue(element));
        }

        @Override
        public int compare(Object v1, Object v2) {
          return CompareMe.compare((Double) v1, (Double) v2);
        }
      }).build();

  final IGenericTableColumn columnPrice = new GenericTableColumnBuilder("Price")
      .align(SWT.RIGHT).textExtent(EXTENT_COLUMN_PRICE)
      .provider(new ICellDataProvider() {

        @Override
        public Object getCellValue(Object element) {
          OrderWrapper order = (OrderWrapper) element;
          return Double.valueOf((Long.valueOf(order.getPrice()).doubleValue() / 100));
        }

        @Override
        public void getCellData(Object element, Object[] data) {
          data[ICellDataProvider.TEXT] = formatDouble
              .format(getCellValue(element));
        }

        @Override
        public int compare(Object v1, Object v2) {
          return CompareMe.compare((Double) v1, (Double) v2);
        }
      }).build();

  final IGenericTableColumn columnQuantity = new GenericTableColumnBuilder(
      "Quantity").align(SWT.RIGHT).textExtent(EXTENT_COLUMN_QUANTITY)
      .provider(new ICellDataProvider() {

        @Override
        public Object getCellValue(Object element) {
          OrderWrapper order = (OrderWrapper) element;
          return Long.valueOf(order.getQuantity());
        }

        @Override
        public void getCellData(Object element, Object[] data) {
          data[ICellDataProvider.TEXT] = Long
              .toString((Long) getCellValue(element));
        }

        @Override
        public int compare(Object v1, Object v2) {
          return CompareMe.compare((Long) v1, (Long) v2);
        }
      }).build();

  final IStructuredContentProvider contentProvider = new IStructuredContentProvider() {

    @Override
    public void dispose() {}

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

    private int scanPendingTransactions(Long orderId) {
      for (Transaction t : nxt.getPendingTransactions()) {

        /* Order has a pending cancellation */

        if (t.getType().equals(
            TransactionType.ColoredCoins.BID_ORDER_CANCELLATION)) {
          Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) t
              .getAttachment();
          if (attachment.getOrderId().equals(orderId)) {
            return STATE_CANCELLED;
          }
        }

        /* Order is in pending list */

        else if (t.getType().equals(
            TransactionType.ColoredCoins.BID_ORDER_PLACEMENT)) {
          if (t.getId().equals(orderId)) {
            return STATE_PENDING;
          }
        }
      }
      return 0;
    }

    @Override
    public Object[] getElements(Object inputElement) {
      IUser user = userService.getActiveUser();
      if (user == null) {
        return new Object[0];
      }

      Account account = user.getAccount().getNative();
      if (account == null) {
        return new Object[0];
      }

      Asset asset = exchange.getSelectedAsset();
      if (asset == null) {
        return new Object[0];
      }

      List<OrderWrapper> elements = new ArrayList<OrderWrapper>();

      /* Add all pending orders */
      for (Transaction t : nxt.getPendingTransactions()) {
        if (t.getType()
            .equals(TransactionType.ColoredCoins.BID_ORDER_PLACEMENT)) {
          Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) t
              .getAttachment();
          if (attachment.getAssetId().equals(asset.getId())) {
            if (t.getSenderId().equals(account.getId())) {
              elements.add(new OrderWrapper(t.getId(), attachment.getPrice(),
                  attachment.getQuantity()));
            }
          }
        }
      }

      List<Transaction> remove = new ArrayList<Transaction>();

      SortedSet<Order.Bid> bids = Order.Bid.getSortedOrders(asset.getId());
      for (Order.Bid bid : bids) {
        if (bid.getAccount().equals(account)) {

          int state = scanPendingTransactions(bid.getId());
          if (state == STATE_CANCELLED) {
            continue;
          }
          else if (state == STATE_PENDING) {
            remove.add(Nxt.getBlockchain().getTransaction(bid.getId()));
          }
          else {
            elements.add(new OrderWrapper(bid));
          }
        }
      }

      /* Clean up pending transactions */
      nxt.getPendingTransactions().removeAll(remove);

      return elements.toArray(new Object[elements.size()]);
    }
  };

  public INxtService nxt;
  private IUserService userService;
  private IAssetExchange exchange;
  static int STATE_CANCELLED = 1;
  static int STATE_PENDING = 2;

  public MyBuyOrdersViewer(Composite parent, INxtService nxt,
      IAssetExchange exchange, IUserService userService) {
    super(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.SINGLE
        | SWT.BORDER);
    this.nxt = nxt;
    this.userService = userService;
    this.exchange = exchange;
    setGenericTable(new IGenericTable() {

      @Override
      public int getDefaultSortDirection() {
        return GenericComparator.DESCENDING;
      }

      @Override
      public IGenericTableColumn getDefaultSortColumn() {
        return columnPrice;
      }

      @Override
      public IStructuredContentProvider getContentProvider() {
        return contentProvider;
      }

      @Override
      public IGenericTableColumn[] getColumns() {
        return new IGenericTableColumn[] { columnPending, columnTotal,
            columnPrice, columnQuantity };
      }
    });
    setInput(1);
  }
}
