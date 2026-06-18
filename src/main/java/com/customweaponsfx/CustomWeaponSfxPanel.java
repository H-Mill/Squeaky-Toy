package com.customweaponsfx;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class CustomWeaponSfxPanel extends PluginPanel
{
	static final String BUNDLED_PREFIX = "bundled:";
	private static final String BUILTIN_SUFFIX = " (built-in)";

	static final String RECEIVED_GROUPS_PREFIX = "defaultReceived";
	static final String GLOBAL_WEAPON_GROUPS_PREFIX = "globalWeapon";

	private static final float TITLE_SIZE = 22f;
	private static final float SECTION_TITLE_SIZE = 16f;

	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_HOVER_ICON;
	private static final ImageIcon EDIT_ICON;
	private static final ImageIcon EDIT_HOVER_ICON;
	private static final ImageIcon EXPAND_ICON;
	private static final ImageIcon EXPAND_HOVER_ICON;
	private static final ImageIcon COLLAPSE_ICON;
	private static final ImageIcon COLLAPSE_HOVER_ICON;
	private static final ImageIcon TRASH_ICON;
	private static final ImageIcon TRASH_HOVER_ICON;
	private static final ImageIcon REFRESH_ICON;
	private static final ImageIcon REFRESH_HOVER_ICON;
	private static final BufferedImage REFRESH_IMG;

	static
	{
		final BufferedImage deleteImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "delete_icon.png");
		DELETE_ICON = new ImageIcon(deleteImg);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(deleteImg, -100));

		final BufferedImage editImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "edit_icon.png");
		EDIT_ICON = new ImageIcon(editImg);
		EDIT_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(editImg, -100));

		// right_arrow = collapsed (click to expand), down_arrow = expanded (click to collapse)
		final BufferedImage expandImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "right_arrow.png");
		EXPAND_ICON = new ImageIcon(expandImg);
		EXPAND_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(expandImg, -100));

		final BufferedImage collapseImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "down_arrow.png");
		COLLAPSE_ICON = new ImageIcon(collapseImg);
		COLLAPSE_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(collapseImg, -100));

		final BufferedImage trashImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "trash.png");
		TRASH_ICON = new ImageIcon(trashImg);
		TRASH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(trashImg, -100));

		final BufferedImage refreshImg = ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "refresh.png");
		REFRESH_IMG = refreshImg;
		REFRESH_ICON = new ImageIcon(refreshImg);
		REFRESH_HOVER_ICON = new ImageIcon(ImageUtil.luminanceOffset(refreshImg, -100));
	}

	private List<String> bundledSounds = new ArrayList<>();
	private final Set<Integer> expandedWeapons = new HashSet<>();
	private final Set<String> expandedDefaults = new HashSet<>();

	/** Active spin animation for the refresh button, if any; restarted (not stacked) on rapid re-clicks. */
	private Timer refreshSpinTimer;

	private final CustomWeaponSfxConfigStore store;
	private final ItemManager itemManager;
	private final Runnable onOpenSearch;
	private final Runnable onAddEquipped;
	private final Consumer<Integer> onRemoveWeapon;
	private final BiConsumer<String, Integer> onTestSound;
	private final Runnable onReset;
	private final Runnable onRefreshSounds;
	private final BiConsumer<SfxOption, Boolean> onOptionToggled;
	private final Consumer<String> onExcludedNpcIdsChanged;
	private final Consumer<String> onExcludedNpcNamesChanged;

	private JCheckBox ignoreSmallMaxCheckBox;
	private JCheckBox ignoreZeroPrayerCheckBox;
	private JCheckBox ignoreZeroThrallCheckBox;
	private JCheckBox dontOverrideGlobalCheckBox;
	private JTextField excludedNpcIdsField;
	private JTextField excludedNpcNamesField;

	private final JPanel weaponListPanel;
	private final JPanel mainContent;
	public CustomWeaponSfxPanel(CustomWeaponSfxConfigStore store,
		ItemManager itemManager,
		Runnable onOpenSearch,
		Runnable onAddEquipped,
		Consumer<Integer> onRemoveWeapon,
		BiConsumer<String, Integer> onTestSound,
		Runnable onReset,
		Runnable onRefreshSounds,
		BiConsumer<SfxOption, Boolean> onOptionToggled,
		Consumer<String> onExcludedNpcIdsChanged,
		Consumer<String> onExcludedNpcNamesChanged)
	{
		this.store = store;
		this.itemManager = itemManager;
		this.onOpenSearch = onOpenSearch;
		this.onAddEquipped = onAddEquipped;
		this.onRemoveWeapon = onRemoveWeapon;
		this.onTestSound = onTestSound;
		this.onReset = onReset;
		this.onRefreshSounds = onRefreshSounds;
		this.onOptionToggled = onOptionToggled;
		this.onExcludedNpcIdsChanged = onExcludedNpcIdsChanged;
		this.onExcludedNpcNamesChanged = onExcludedNpcNamesChanged;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		weaponListPanel = boxColumn(null);

		mainContent = boxColumn(null);
		mainContent.add(buildTopPanel());
		mainContent.add(weaponListPanel);

		add(mainContent, BorderLayout.NORTH);
	}

	private JPanel buildTopPanel()
	{
		JPanel top = boxColumn(null);

		// Match the Look-and-Feel's default button font so labels read consistently with the buttons.
		Font buttonFont = new JButton().getFont();

		JPanel titleRow = new JPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("Custom Weapon SFX");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		setBoldFont(title, TITLE_SIZE);
		titleRow.add(title);

		titleRow.add(Box.createHorizontalGlue());

		JButton resetBtn = new JButton(TRASH_ICON);
		resetBtn.setRolloverIcon(TRASH_HOVER_ICON);
		resetBtn.setToolTipText("Reset All Data");
		resetBtn.setMargin(new Insets(2, 6, 2, 6));
		resetBtn.addActionListener(e ->
		{
			if (confirmYesNo("Reset all weapons and sound groups back to defaults?", "Reset All Data"))
				onReset.run();
		});
		titleRow.add(resetBtn);

		top.add(titleRow);
		top.add(Box.createVerticalStrut(4));

		JLabel customSoundDirections = new JLabel("<html>Want a custom sfx?<br>" +
				"1. Place <b>.wav</b> files in:</html>");
		customSoundDirections.setFont(buttonFont);
		customSoundDirections.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(customSoundDirections);

		JButton folderLink = new JButton("<html><u>.runelite/customweaponsfx/</u></html>");
		folderLink.setFont(buttonFont);
		folderLink.setBorderPainted(false);
		folderLink.setContentAreaFilled(false);
		folderLink.setFocusPainted(false);
		folderLink.setForeground(Color.CYAN);
		folderLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		folderLink.setAlignmentX(Component.LEFT_ALIGNMENT);
		folderLink.addActionListener(e -> LinkBrowser.open(CustomWeaponSfxPlugin.SOUNDS_DIR.toString()));
		top.add(folderLink);

		JPanel refreshRow = new JPanel();
		refreshRow.setLayout(new BoxLayout(refreshRow, BoxLayout.X_AXIS));
		refreshRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel step2 = new JLabel("2. Click Refresh Sounds:");
		step2.setFont(buttonFont);
		refreshRow.add(step2);
		refreshRow.add(Box.createHorizontalStrut(4));

		JButton refreshSoundsBtn = new JButton(REFRESH_ICON);
		refreshSoundsBtn.setRolloverIcon(REFRESH_HOVER_ICON);
		refreshSoundsBtn.setToolTipText("Rescan .runelite/customweaponsfx/ for new .wav files");
		refreshSoundsBtn.setMargin(new Insets(2, 6, 2, 6));
		refreshSoundsBtn.addActionListener(e ->
		{
			spinRefreshButton(refreshSoundsBtn);
			onRefreshSounds.run();
		});
		refreshRow.add(refreshSoundsBtn);
		refreshRow.add(Box.createHorizontalGlue());
		top.add(refreshRow);

		JLabel customSoundDirections2 = new JLabel("<html>3. Click an Add method below and configure it</html>");
		customSoundDirections2.setFont(buttonFont);
		customSoundDirections2.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(customSoundDirections2);
		top.add(Box.createVerticalStrut(8));

		JPanel btnRow = new JPanel();
		btnRow.setLayout(new BoxLayout(btnRow, BoxLayout.X_AXIS));
		btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton addSearchBtn = new JButton("Add (Search)");
		addSearchBtn.setToolTipText("Search for a weapon to configure");
		addSearchBtn.addActionListener(e -> onOpenSearch.run());
		btnRow.add(addSearchBtn);

		btnRow.add(Box.createHorizontalStrut(4));

		JButton addEquippedBtn = new JButton("Add (Equipped)");
		addEquippedBtn.setToolTipText("Add your currently equipped weapon");
		addEquippedBtn.addActionListener(e -> onAddEquipped.run());
		btnRow.add(addEquippedBtn);

		top.add(btnRow);
		top.add(Box.createVerticalStrut(4));

		top.add(Box.createVerticalStrut(6));
		top.add(buildTogglesSection());
		top.add(Box.createVerticalStrut(4));

		return top;
	}

	/** Spins the refresh icon one full turn (~500ms) as click feedback, then restores the static icon. */
	private void spinRefreshButton(JButton button)
	{
		if (refreshSpinTimer != null && refreshSpinTimer.isRunning())
			refreshSpinTimer.stop();

		final int durationMs = 500;
		final long start = System.currentTimeMillis();
		// Rollover would otherwise paint the dark hover icon over our rotated frames while hovered.
		button.setRolloverEnabled(false);
		refreshSpinTimer = new Timer(15, null);
		refreshSpinTimer.addActionListener(ev ->
		{
			long elapsed = System.currentTimeMillis() - start;
			if (elapsed >= durationMs)
			{
				button.setIcon(REFRESH_ICON);
				button.setRolloverEnabled(true);
				refreshSpinTimer.stop();
				return;
			}
			double angle = -2 * Math.PI * (elapsed / (double) durationMs);
			button.setIcon(new ImageIcon(rotateImage(REFRESH_IMG, angle)));
		});
		refreshSpinTimer.setInitialDelay(0);
		refreshSpinTimer.start();
	}

	/** Returns a copy of {@code src} rotated {@code radians} about its centre, same dimensions. */
	private static BufferedImage rotateImage(BufferedImage src, double radians)
	{
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.rotate(radians, w / 2.0, h / 2.0);
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}

	private JPanel buildTogglesSection()
	{
		JPanel section = boxColumn(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)
		));

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton collapseBtn = makeCollapseButton(true);
		headerRow.add(collapseBtn, BorderLayout.WEST);

		JLabel headerLabel = new JLabel("Options");
		headerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		headerLabel.setToolTipText("<html>Global settings that filter out unwanted SFX and let you<br>"
			+ "exclude specific NPCs from triggering sounds.</html>");
		setBoldFont(headerLabel, SECTION_TITLE_SIZE);
		headerRow.add(headerLabel, BorderLayout.CENTER);

		section.add(headerRow);

		JPanel content = boxColumn(ColorScheme.DARKER_GRAY_COLOR);
		content.setVisible(false);

		content.add(Box.createVerticalStrut(4));

		ignoreSmallMaxCheckBox = buildOptionCheckBox(SfxOption.IGNORE_SMALL_MAX, "Ignore max hits ≤ 3",
			"<html>When enabled, max hit SFX will not play if the hit<br>"
				+ "damage is 3 or less. Prevents thrall max hits from<br>"
				+ "triggering max hit sounds.</html>");
		content.add(ignoreSmallMaxCheckBox);

		ignoreZeroPrayerCheckBox = buildOptionCheckBox(SfxOption.IGNORE_RECEIVED_ZERO_PRAYER, "Ignore zeroes with prayer",
			"<html>When enabled, the Received Attacks 'Regular zero'<br>"
				+ "trigger will not fire while Protect from Melee,<br>"
				+ "Protect from Ranged, or Protect from Magic is active.</html>");
		content.add(ignoreZeroPrayerCheckBox);

		ignoreZeroThrallCheckBox = buildOptionCheckBox(SfxOption.IGNORE_ZERO_THRALL, "Ignore zeroes with thrall",
			"<html>When enabled, zero-damage triggers will not fire<br>"
				+ "while a thrall is active.</html>");
		content.add(ignoreZeroThrallCheckBox);

		dontOverrideGlobalCheckBox = buildOptionCheckBox(SfxOption.DONT_OVERRIDE_GLOBAL, "Don't override Global",
			"<html>When enabled, weapon-specific triggers no longer override<br>"
				+ "the matching Global (All Weapons) triggers — both sounds play.</html>");
		content.add(dontOverrideGlobalCheckBox);

		content.add(Box.createVerticalStrut(6));
		content.add(buildExcludedNpcRow(
			"Excluded NPC IDs:",
			"<html>Comma-separated NPC ids whose hitsplats never trigger SFX.<br>"
				+ "Example: 11706, 11707</html>",
			store.getExcludedNpcIdsRaw(), onExcludedNpcIdsChanged, f -> excludedNpcIdsField = f));

		content.add(Box.createVerticalStrut(6));
		content.add(buildExcludedNpcRow(
			"Excluded NPC Names:",
			"<html>Comma-separated NPC names whose hitsplats never trigger SFX.<br>"
				+ "Matching is case-insensitive.<br>"
				+ "Example: Vorkath, zulrah</html>",
			store.getExcludedNpcNamesRaw(), onExcludedNpcNamesChanged, f -> excludedNpcNamesField = f));

		section.add(content);

		collapseBtn.addActionListener(e ->
		{
			boolean nowVisible = content.isVisible();
			content.setVisible(!nowVisible);
			applyCollapseState(collapseBtn, nowVisible);
			section.revalidate();
			section.repaint();
		});

		return section;
	}

	private JCheckBox buildOptionCheckBox(SfxOption option, String label, String tooltip)
	{
		JCheckBox box = new JCheckBox(label, store.getBool(option));
		box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setToolTipText(tooltip);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.addActionListener(e -> onOptionToggled.accept(option, box.isSelected()));
		return box;
	}

	private JPanel buildExcludedNpcRow(String label, String tooltip, String initialValue,
		Consumer<String> onChange, Consumer<JTextField> fieldSink)
	{
		JPanel panel = boxColumn(ColorScheme.DARKER_GRAY_COLOR);

		JLabel lbl = new JLabel(label);
		lbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		lbl.setToolTipText(tooltip);
		panel.add(lbl);
		panel.add(Box.createVerticalStrut(2));

		JTextField field = new JTextField(initialValue);
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
		field.setToolTipText(tooltip);
		field.addActionListener(e -> onChange.accept(field.getText()));
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				onChange.accept(field.getText());
			}
		});
		panel.add(field);

		fieldSink.accept(field);
		return panel;
	}

	public void resetToggles()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (ignoreSmallMaxCheckBox != null) ignoreSmallMaxCheckBox.setSelected(true);
			if (ignoreZeroPrayerCheckBox != null) ignoreZeroPrayerCheckBox.setSelected(true);
			if (ignoreZeroThrallCheckBox != null) ignoreZeroThrallCheckBox.setSelected(true);
			if (dontOverrideGlobalCheckBox != null) dontOverrideGlobalCheckBox.setSelected(false);
			if (excludedNpcIdsField != null) excludedNpcIdsField.setText("");
			if (excludedNpcNamesField != null) excludedNpcNamesField.setText("");
		});
	}

	public void rebuild(List<WeaponEntry> weapons, List<String> availableSounds,
		List<String> bundledSounds, List<TriggerGroup> receivedGroups, List<TriggerGroup> globalWeaponGroups)
	{
		this.bundledSounds = bundledSounds;
		SwingUtilities.invokeLater(() ->
		{
			weaponListPanel.removeAll();

			weaponListPanel.add(buildDefaultRowGroups(
				"Received Attacks", "<html>Sounds that play when <b>you take a hit</b>, regardless of which<br>"
					+ "weapon you have equipped. Configure sound groups and triggers<br>"
					+ "for incoming attacks here.</html>",
				RECEIVED_GROUPS_PREFIX, receivedGroups, availableSounds,
				EnumSet.of(Triggers.REGULAR_ZERO, Triggers.REGULAR_HIT, Triggers.ALL, Triggers.PLAYER_DEATH), SfxOption.RECEIVED_ENABLED));
			weaponListPanel.add(Box.createVerticalStrut(4));

			weaponListPanel.add(buildDefaultRowGroups(
				"Global (All Weapons)", "<html>Sounds that play for <b>every weapon</b> you attack with,<br>"
					+ "in addition to any weapon-specific sounds configured below.<br><br>"
					+ "<b>Note:</b> if the equipped weapon has its own sound group for a<br>"
					+ "trigger, that weapon <b>overrides</b> the global sound for that trigger —<br>"
					+ "they do not stack (unless <b>Don't override Global</b> in the Options<br>"
					+ "section is enabled.</html>",
				GLOBAL_WEAPON_GROUPS_PREFIX, globalWeaponGroups, availableSounds,
				EnumSet.complementOf(EnumSet.of(Triggers.REGULAR_HIT, Triggers.PLAYER_DEATH)), SfxOption.GLOBAL_ENABLED));
			weaponListPanel.add(Box.createVerticalStrut(4));


			for (int i = 0; i < weapons.size(); i++)
			{
				weaponListPanel.add(buildRow(weapons.get(i), availableSounds, i));
				weaponListPanel.add(Box.createVerticalStrut(8));
			}

			weaponListPanel.revalidate();
			weaponListPanel.repaint();
		});
	}

	private JPanel buildDefaultRowGroups(String label, String tooltip, String prefix,
		List<TriggerGroup> groups, List<String> availableSounds, Set<Triggers> visibleTriggers,
		SfxOption enabledOption)
	{
		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		nameLabel.setToolTipText(tooltip);
		setBoldFont(nameLabel, SECTION_TITLE_SIZE);

		List<Component> eastControls = new ArrayList<>();
		if (enabledOption != null)
		{
			JCheckBox enabledBox = new JCheckBox();
			enabledBox.setSelected(store.getBool(enabledOption));
			enabledBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			enabledBox.setToolTipText("Enable or disable this section");
			enabledBox.addActionListener(e -> onOptionToggled.accept(enabledOption, enabledBox.isSelected()));
			eastControls.add(enabledBox);
		}

		return buildCollapsibleGroupsPanel(
			expandedDefaults, prefix,
			ColorScheme.DARKER_GRAY_COLOR, ColorScheme.BRAND_ORANGE,
			null, nameLabel, eastControls, 0,
			groups, availableSounds,
			() -> store.saveDefaultGroups(prefix, groups), visibleTriggers);
	}

	private static final Color ROW_COLOR_A = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW_COLOR_B = new Color(40, 38, 35);

	/** Background for each sound's box — matches the weapon boxes to set sounds apart from the group. */
	private static final Color SOUND_BOX_COLOR = ColorScheme.DARKER_GRAY_COLOR;

	private JPanel buildRow(WeaponEntry entry, List<String> availableSounds, int index)
	{
		Color bg = (index % 2 == 0) ? ROW_COLOR_A : ROW_COLOR_B;

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(32, 32));
		iconLabel.setToolTipText(entry.getWeaponName());
		AsyncBufferedImage icon = itemManager.getImage(entry.getItemId());
		if (icon != null)
		{
			icon.addTo(iconLabel);
		}

		JLabel nameLabel = new JLabel(entry.getWeaponName());
		nameLabel.setForeground(entry.isEnabled() ? Color.WHITE : Color.GRAY);
		setBoldFont(nameLabel, SECTION_TITLE_SIZE);
		nameLabel.setToolTipText(entry.getWeaponName());

		JCheckBox enabledBox = new JCheckBox();
		enabledBox.setSelected(entry.isEnabled());
		enabledBox.setBackground(bg);
		enabledBox.setToolTipText("Enable or disable this weapon");
		enabledBox.addActionListener(e ->
		{
			entry.setEnabled(enabledBox.isSelected());
			nameLabel.setForeground(entry.isEnabled() ? Color.WHITE : Color.GRAY);
			store.saveWeaponEnabled(entry.getItemId(), entry.isEnabled());
		});

		JButton removeBtn = makeRemoveButton("Remove weapon");
		removeBtn.addActionListener(e ->
		{
			if (confirmYesNo("Remove " + entry.getWeaponName() + " and all its sound groups?", "Remove Weapon"))
				onRemoveWeapon.accept(entry.getItemId());
		});

		List<Component> eastControls = new ArrayList<>();
		eastControls.add(enabledBox);
		eastControls.add(removeBtn);

		return buildCollapsibleGroupsPanel(
			expandedWeapons, entry.getItemId(),
			bg, ColorScheme.MEDIUM_GRAY_COLOR,
			iconLabel, nameLabel, eastControls, 36,
			entry.getGroups(), availableSounds,
			() -> store.saveWeaponGroups(entry),
			EnumSet.complementOf(EnumSet.of(Triggers.REGULAR_HIT, Triggers.PLAYER_DEATH)));
	}

	/**
	 * Builds a bordered, collapsible panel whose body is a {@link #rebuildGroupsSection} list of
	 * sound groups. Shared by the weapon rows and the Received/Global default sections; the only
	 * differences are the colours, the optional leading icon, the header's east controls, and which
	 * expansion set tracks the collapsed state (keyed by {@code key}).
	 */
	private <T> JPanel buildCollapsibleGroupsPanel(
		Set<T> expandedSet, T key,
		Color bg, Color borderColor,
		Component westLeading,
		JLabel nameLabel,
		List<Component> eastControls,
		int headerMaxHeight,
		List<TriggerGroup> groups, List<String> availableSounds,
		Runnable onSave, Set<Triggers> visibleTriggers)
	{
		boolean collapsed = !expandedSet.contains(key);

		JPanel panel = boxColumn(bg);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor),
			new EmptyBorder(6, 6, 6, 6)
		));

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(bg);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (headerMaxHeight > 0)
			headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, headerMaxHeight));

		JButton collapseBtn = makeCollapseButton(collapsed);
		if (westLeading != null)
		{
			JPanel westBlock = new JPanel(new BorderLayout(4, 0));
			westBlock.setBackground(bg);
			westBlock.add(collapseBtn, BorderLayout.WEST);
			westBlock.add(westLeading, BorderLayout.EAST);
			headerRow.add(westBlock, BorderLayout.WEST);
		}
		else
		{
			headerRow.add(collapseBtn, BorderLayout.WEST);
		}

		headerRow.add(nameLabel, BorderLayout.CENTER);

		if (eastControls != null && !eastControls.isEmpty())
		{
			JPanel eastBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			eastBlock.setBackground(bg);
			for (Component c : eastControls) eastBlock.add(c);
			headerRow.add(eastBlock, BorderLayout.EAST);
		}

		panel.add(headerRow);

		Component strut = Box.createVerticalStrut(6);
		strut.setVisible(!collapsed);
		panel.add(strut);

		JPanel groupsHolder = boxColumn(bg);
		groupsHolder.setVisible(!collapsed);
		rebuildGroupsSection(groupsHolder, groups, availableSounds, onSave, visibleTriggers);
		panel.add(groupsHolder);

		collapseBtn.addActionListener(e ->
		{
			boolean nowCollapsed = expandedSet.contains(key);
			if (nowCollapsed)
				expandedSet.remove(key);
			else
				expandedSet.add(key);
			applyCollapseState(collapseBtn, nowCollapsed);
			strut.setVisible(!nowCollapsed);
			groupsHolder.setVisible(!nowCollapsed);
			panel.revalidate();
			panel.repaint();
		});

		return panel;
	}

	private void rebuildGroupsSection(JPanel holder, List<TriggerGroup> groups,
		List<String> availableSounds, Runnable onSave, Set<Triggers> visibleTriggers)
	{
		holder.removeAll();

		for (int i = 0; i < groups.size(); i++)
		{
			final int idx = i;
			TriggerGroup group = groups.get(i);

			JPanel groupPanel = boxColumn(ColorScheme.DARK_GRAY_COLOR);
			groupPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(4, 4, 4, 4)
			));

			JPanel groupHeader = new JPanel(new BorderLayout());
			groupHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
			groupHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

			String groupName = group.getName() != null ? group.getName() : "Sound Group " + (i + 1);
			JLabel groupLabel = new JLabel(groupName);
			groupLabel.setForeground(ColorScheme.BRAND_ORANGE);
			groupLabel.setToolTipText("<html>A sound group plays when its triggers are met. If the group has<br>"
				+ "multiple sounds, one is picked at random, weighted by each sound's weight.<br><br>"
				+ "You can add multiple sound groups — if several groups share<br>"
				+ "overlapping triggers, they all play. Use separate groups for<br>"
				+ "different triggers, or to stack multiple sounds on the same trigger.</html>");
			setBoldFont(groupLabel, SECTION_TITLE_SIZE);
			groupHeader.add(groupLabel, BorderLayout.WEST);

			JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			headerButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

			JButton renameGroupBtn = makeImageButton(EDIT_ICON, EDIT_HOVER_ICON, "Rename sound group");
			renameGroupBtn.addActionListener(e ->
			{
				String input = JOptionPane.showInputDialog(this, "Group name:", groupName);
				if (input == null) return; // cancelled
				String trimmed = input.trim();
				if (trimmed.isEmpty())
				{
					JOptionPane.showMessageDialog(this,
						"Name cannot be empty.", "Invalid name", JOptionPane.WARNING_MESSAGE);
					return;
				}
				if (TriggerGroup.isNameTaken(groups, trimmed, group))
				{
					JOptionPane.showMessageDialog(this,
						"A sound group named \"" + trimmed + "\" already exists.",
						"Duplicate name", JOptionPane.WARNING_MESSAGE);
					return;
				}
				group.setName(trimmed);
				onSave.run();
				rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
			});
			headerButtons.add(renameGroupBtn);

			JButton removeGroupBtn = makeRemoveButton("Remove sound group");
			removeGroupBtn.addActionListener(e ->
			{
				if (!confirmYesNo("Remove \"" + groupName + "\"?", "Remove Sound Group")) return;
				groups.remove(idx);
				onSave.run();
				rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
			});
			headerButtons.add(removeGroupBtn);

			groupHeader.add(headerButtons, BorderLayout.EAST);

			JPanel soundsHolder = boxColumn(ColorScheme.DARK_GRAY_COLOR);
			rebuildSoundsHolder(soundsHolder, group, availableSounds, onSave);

			groupPanel.add(groupHeader);
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(buildSliderRow(ColorScheme.DARK_GRAY_COLOR, "Chance:",
				"Probability that this sound group plays when its trigger fires (100 = always, 0 = never)",
				group.getChance(), v -> { group.setChance(v); onSave.run(); }));
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(soundsHolder);
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(buildTriggersPanel(group.getTriggers(), onSave, visibleTriggers));

			holder.add(groupPanel);
			if (i < groups.size() - 1)
				holder.add(Box.createVerticalStrut(4));
		}

		holder.add(Box.createVerticalStrut(4));
		JButton addGroupBtn = new JButton("+ Add Sound Group");
		addGroupBtn.setToolTipText("Add a new sound group with its own triggers, chance, and sounds");
		addGroupBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		addGroupBtn.addActionListener(e ->
		{
			List<SoundEntry> newSounds = new ArrayList<>();
			newSounds.add(new SoundEntry("", 75));
			TriggerGroup newGroup = new TriggerGroup(EnumSet.noneOf(Triggers.class), newSounds, 100);
			newGroup.setName(TriggerGroup.defaultName(groups));
			groups.add(newGroup);
			onSave.run();
			rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
		});
		holder.add(addGroupBtn);

		holder.revalidate();
		holder.repaint();
	}

	private JPanel buildTriggersPanel(Set<Triggers> enabledTriggers, Runnable onSave, Set<Triggers> visibleTriggers)
	{
		JPanel panel = boxColumn(ColorScheme.DARK_GRAY_COLOR);

		JLabel lbl = new JLabel("Triggers:");
		lbl.setForeground(Color.WHITE);
		lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
		lbl.setToolTipText("Which hit outcomes cause this sound group to play");
		panel.add(lbl);
		panel.add(Box.createVerticalStrut(2));

		for (Triggers trigger : Triggers.values())
		{
			if (!visibleTriggers.contains(trigger)) continue;
			JCheckBox box = new JCheckBox(trigger.getName());
			box.setForeground(Color.LIGHT_GRAY);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setToolTipText(triggerTooltip(trigger));
			box.setSelected(enabledTriggers.contains(trigger));
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.addActionListener(e ->
			{
				if (box.isSelected()) enabledTriggers.add(trigger);
				else enabledTriggers.remove(trigger);
				onSave.run();
			});
			panel.add(box);
		}

		return panel;
	}

	/** A short description of when each trigger fires, shown as the trigger checkbox's tooltip. */
	private static String triggerTooltip(Triggers trigger)
	{
		switch (trigger)
		{
			case REGULAR_ZERO:
				return "Fires when a regular (non-special) attack deals 0 damage";
			case REGULAR_HIT:
				return "Fires when a regular (non-special) attack deals 1 or more damage";
			case REGULAR_MAX:
				return "Fires when a regular (non-special) attack deals your maximum possible hit";
			case SPECIAL_ZERO:
				return "Fires when a special attack deals 0 damage";
			case SPECIAL_HIT:
				return "Fires when a special attack deals 1 or more damage";
			case SPECIAL_MAX:
				return "Fires when a special attack deals your maximum possible hit";
			case ALL:
				return "Fires on every attack, regardless of the outcome";
			case KILL:
				return "Fires when your attack kills the target";
			case PLAYER_DEATH:
				return "Fires when you die";
			default:
				return null;
		}
	}

	private void rebuildSoundsHolder(JPanel holder, TriggerGroup group,
		List<String> availableSounds, Runnable onSave)
	{
		holder.removeAll();
		List<SoundEntry> sounds = group.getSounds();
		final boolean weighted = sounds.size() > 1;
		final List<JLabel> weightReadouts = new ArrayList<>();

		// Re-derives each weight row's effective "%" from the current weights across all sounds.
		Runnable refreshWeights = () ->
		{
			double total = 0;
			for (SoundEntry s : sounds) total += Math.max(0, s.getWeight());
			for (int k = 0; k < weightReadouts.size(); k++)
			{
				double w = Math.max(0, sounds.get(k).getWeight());
				weightReadouts.get(k).setText(total <= 0 ? "0%" : Math.round(w * 100 / total) + "%");
			}
		};

		for (int j = 0; j < sounds.size(); j++)
		{
			final int idx = j;
			SoundEntry se = sounds.get(j);

			// Each sound lives in its own box, styled like the weapon boxes, to set it apart.
			JPanel soundBox = boxColumn(SOUND_BOX_COLOR);
			soundBox.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(6, 6, 6, 6)
			));

			// BorderLayout keeps the remove button pinned to the right edge so it can't be
			// pushed off-panel when the box border narrows the available width.
			JPanel soundRow = new JPanel(new BorderLayout(4, 0));
			soundRow.setBackground(SOUND_BOX_COLOR);
			soundRow.setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel soundControls = flowRow(SOUND_BOX_COLOR);
			JLabel lbl = new JLabel("Sound:");
			lbl.setForeground(Color.WHITE);
			lbl.setToolTipText("Sound file to play. Built-in sounds are bundled with the plugin; custom sounds load from .runelite/customweaponsfx/");
			soundControls.add(lbl);

			String[] options = buildSoundOptions(availableSounds);
			JComboBox<String> box = new JComboBox<>(options);
			box.setPreferredSize(new Dimension(75, box.getPreferredSize().height));
			box.setSelectedItem(configToDisplay(se.getSoundFile()));
			box.addActionListener(e ->
			{
				se.setSoundFile(displayToConfig((String) box.getSelectedItem()));
				onSave.run();
			});
			soundControls.add(box);

			JButton testBtn = makeIconButton("▶", "Test sound");
			testBtn.addActionListener(e -> onTestSound.accept(
				displayToConfig((String) box.getSelectedItem()), se.getVolume()));
			soundControls.add(testBtn);

			soundRow.add(soundControls, BorderLayout.CENTER);

			if (sounds.size() > 1)
			{
				JButton removeBtn = makeRemoveButton("Remove this sound");
				removeBtn.addActionListener(e ->
				{
					sounds.remove(idx);
					onSave.run();
					rebuildSoundsHolder(holder, group, availableSounds, onSave);
				});
				soundRow.add(removeBtn, BorderLayout.EAST);
			}

			soundRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, soundRow.getPreferredSize().height));
			soundBox.add(soundRow);
			soundBox.add(buildSliderRow(SOUND_BOX_COLOR, "Volume:",
				"Playback volume for this sound (0 = silent, 100 = full volume)",
				se.getVolume(), v -> { se.setVolume(v); onSave.run(); }));

			if (weighted)
				soundBox.add(buildWeightRow(SOUND_BOX_COLOR, se, weightReadouts, refreshWeights, onSave));

			holder.add(soundBox);
			if (j < sounds.size() - 1)
				holder.add(Box.createVerticalStrut(4));
		}

		refreshWeights.run();

		holder.add(Box.createVerticalStrut(2));
		JButton addSoundBtn = new JButton("+ Add Sound");
		addSoundBtn.setToolTipText("Add another sound to this group — when it fires, one is picked at random, weighted by each sound's weight");
		addSoundBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		addSoundBtn.addActionListener(e ->
		{
			sounds.add(new SoundEntry("", 75));
			onSave.run();
			rebuildSoundsHolder(holder, group, availableSounds, onSave);
		});
		holder.add(addSoundBtn);

		holder.revalidate();
		holder.repaint();
	}

	/**
	 * A "Weight:" spinner row whose readout shows this sound's <em>effective</em> chance
	 * (its weight relative to the group's total). Live-updates every row's readout via
	 * {@code refreshWeights} and commits via {@code onSave}.
	 */
	private JPanel buildWeightRow(Color bg, SoundEntry se, List<JLabel> weightReadouts,
		Runnable refreshWeights, Runnable onSave)
	{
		JPanel row = flowRow(bg);
		JLabel lbl = new JLabel("Weight:");
		lbl.setForeground(Color.WHITE);
		lbl.setToolTipText("Relative weight for picking this sound. Its chance to play is this "
			+ "weight divided by the sum of all weights in the group (0 = never). "
			+ "The readout shows the resulting chance.");
		row.add(lbl);

		double initial = Math.max(0, se.getWeight());
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, 0d, 1000d, 0.5d));
		spinner.setPreferredSize(new Dimension(60, spinner.getPreferredSize().height));
		JLabel readout = new JLabel();
		readout.setForeground(Color.LIGHT_GRAY);
		weightReadouts.add(readout);
		spinner.addChangeListener(e ->
		{
			se.setWeight(((Number) spinner.getValue()).doubleValue());
			refreshWeights.run();
			onSave.run();
		});
		row.add(spinner);
		row.add(readout);
		return row;
	}

	/** A label + 0–100 slider + live "%" readout row; commits via {@code onCommit} on release. */
	private JPanel buildSliderRow(Color bg, String label, String tooltip, int initial, IntConsumer onCommit)
	{
		JPanel row = flowRow(bg);
		JLabel lbl = new JLabel(label);
		lbl.setForeground(Color.WHITE);
		lbl.setToolTipText(tooltip);
		row.add(lbl);
		JSlider slider = new JSlider(0, 100, initial);
		slider.setPreferredSize(new Dimension(100, 20));
		JLabel val = new JLabel(initial + "%");
		val.setForeground(Color.LIGHT_GRAY);
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			val.setText(v + "%");
			if (!slider.getValueIsAdjusting())
			{
				onCommit.accept(v);
			}
		});
		row.add(slider);
		row.add(val);
		return row;
	}

	private String[] buildSoundOptions(List<String> userSounds)
	{
		String[] options = new String[bundledSounds.size() + userSounds.size()];
		for (int i = 0; i < bundledSounds.size(); i++)
			options[i] = bundledSounds.get(i) + BUILTIN_SUFFIX;
		for (int i = 0; i < userSounds.size(); i++)
			options[bundledSounds.size() + i] = userSounds.get(i);
		return options;
	}

	private String configToDisplay(String configValue)
	{
		if (configValue == null || configValue.isEmpty())
			return "squeak" + BUILTIN_SUFFIX;
		if (configValue.startsWith(BUNDLED_PREFIX))
			return configValue.substring(BUNDLED_PREFIX.length()) + BUILTIN_SUFFIX;
		return configValue;
	}

	private static String displayToConfig(String display)
	{
		if (display == null) return BUNDLED_PREFIX + "squeak";
		if (display.endsWith(BUILTIN_SUFFIX))
			return BUNDLED_PREFIX + display.substring(0, display.length() - BUILTIN_SUFFIX.length());
		return display;
	}

	// ----- shared widget factories ----------------------------------------------------------

	/** A vertical (Y_AXIS), left-aligned panel; {@code bg} may be {@code null} to stay transparent. */
	private static JPanel boxColumn(Color bg)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		if (bg != null) p.setBackground(bg);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	/** A left-aligned {@link FlowLayout} row with a tight 4px horizontal gap. */
	private static JPanel flowRow(Color bg)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setBackground(bg);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JButton makeIconButton(String text, String tooltip)
	{
		JButton b = new JButton(text);
		b.setMargin(new Insets(2, 5, 2, 5));
		b.setToolTipText(tooltip);
		return b;
	}

	/** A borderless, transparent button showing only {@code icon}, swapping to {@code hoverIcon} on rollover. */
	private static JButton makeImageButton(ImageIcon icon, ImageIcon hoverIcon, String tooltip)
	{
		JButton b = new JButton(icon);
		if (hoverIcon != null)
		{
			b.setRolloverIcon(hoverIcon);
		}
		b.setToolTipText(tooltip);
		b.setMargin(new Insets(0, 0, 0, 0));
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setFocusPainted(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	private static JButton makeRemoveButton(String tooltip)
	{
		return makeImageButton(DELETE_ICON, DELETE_HOVER_ICON, tooltip);
	}

	private static JButton makeCollapseButton(boolean collapsed)
	{
		JButton b = makeImageButton(null, null, null);
		applyCollapseState(b, collapsed);
		return b;
	}

	private static void applyCollapseState(JButton btn, boolean collapsed)
	{
		btn.setIcon(collapsed ? EXPAND_ICON : COLLAPSE_ICON);
		btn.setRolloverIcon(collapsed ? EXPAND_HOVER_ICON : COLLAPSE_HOVER_ICON);
		btn.setToolTipText(collapsed ? "Expand" : "Minimize");
	}

	private static void setBoldFont(Component c, float size)
	{
		c.setFont(c.getFont().deriveFont(Font.BOLD, size));
	}

	private boolean confirmYesNo(String message, String title)
	{
		return JOptionPane.showConfirmDialog(this, message, title,
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}
}
