package com.customweaponsfx;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

public class CustomWeaponSfxPanel extends PluginPanel
{
	static final String BUNDLED_PREFIX = "bundled:";
	private static final String BUILTIN_SUFFIX = " (built-in)";

	static final String RECEIVED_GROUPS_PREFIX = "defaultReceived";
	static final String GLOBAL_WEAPON_GROUPS_PREFIX = "globalWeapon";


	private List<String> bundledSounds = new ArrayList<>();
	private final Set<Integer> expandedWeapons = new HashSet<>();
	private final Set<String> expandedDefaults = new HashSet<>();

	private final CustomWeaponSfxConfigStore store;
	private final ItemManager itemManager;
	private final Runnable onOpenSearch;
	private final Runnable onAddEquipped;
	private final Consumer<Integer> onRemoveWeapon;
	private final BiConsumer<String, Integer> onTestSound;
	private final Runnable onReset;
	private final Runnable onRefreshSounds;
	private final BiConsumer<SfxOption, Boolean> onOptionToggled;

	private JCheckBox ignoreSmallMaxCheckBox;
	private JCheckBox ignoreZeroPrayerCheckBox;
	private JCheckBox ignoreZeroThrallCheckBox;

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
		BiConsumer<SfxOption, Boolean> onOptionToggled)
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

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		weaponListPanel = new JPanel();
		weaponListPanel.setLayout(new javax.swing.BoxLayout(weaponListPanel, javax.swing.BoxLayout.Y_AXIS));

		mainContent = new JPanel();
		mainContent.setLayout(new javax.swing.BoxLayout(mainContent, javax.swing.BoxLayout.Y_AXIS));
		mainContent.add(buildTopPanel());
		mainContent.add(weaponListPanel);

		add(mainContent, BorderLayout.NORTH);
	}

	private JPanel buildTopPanel()
	{
		JPanel top = new JPanel();
		top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Custom Weapon SFX");
		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(title);
		top.add(Box.createVerticalStrut(4));

		JLabel customSoundDirections = new JLabel("<html>Want a custom sfx?<br>" +
				"1. Place <b>.wav</b> files in:</html>");
		customSoundDirections.setFont(customSoundDirections.getFont().deriveFont(14f));
		customSoundDirections.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(customSoundDirections);

		JButton folderLink = new JButton("<html><u>.runelite/customweaponsfx/</u></html>");
		folderLink.setFont(folderLink.getFont().deriveFont(14f));
		folderLink.setBorderPainted(false);
		folderLink.setContentAreaFilled(false);
		folderLink.setFocusPainted(false);
		folderLink.setForeground(Color.CYAN);
		folderLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		folderLink.setAlignmentX(Component.LEFT_ALIGNMENT);
		folderLink.addActionListener(e -> LinkBrowser.open(CustomWeaponSfxPlugin.SOUNDS_DIR.toString()));
		top.add(folderLink);

		JLabel customSoundDirections2 = new JLabel("<html>2. Click Refresh Sounds<br>" +
				"3. Click an Add method below and configure it</html>");
		customSoundDirections2.setFont(customSoundDirections2.getFont().deriveFont(14f));
		customSoundDirections2.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(customSoundDirections2);
		top.add(Box.createVerticalStrut(8));

		JPanel btnRow = new JPanel();
		btnRow.setLayout(new javax.swing.BoxLayout(btnRow, javax.swing.BoxLayout.X_AXIS));
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

		JPanel refreshRow = new JPanel();
		refreshRow.setLayout(new javax.swing.BoxLayout(refreshRow, javax.swing.BoxLayout.X_AXIS));
		refreshRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton refreshSoundsBtn = new JButton("Refresh Sounds");
		refreshSoundsBtn.setToolTipText("Rescan .runelite/customweaponsfx/ for new .wav files");
		refreshSoundsBtn.addActionListener(e -> onRefreshSounds.run());
		refreshRow.add(refreshSoundsBtn);

		top.add(refreshRow);
		top.add(Box.createVerticalStrut(4));

		JPanel resetRow = new JPanel();
		resetRow.setLayout(new javax.swing.BoxLayout(resetRow, javax.swing.BoxLayout.X_AXIS));
		resetRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton resetBtn = new JButton("Reset All Data");
		resetBtn.setForeground(Color.RED);
		resetBtn.setToolTipText("Wipe all saved weapon entries and sound groups, then restore defaults");
		resetBtn.addActionListener(e ->
		{
			int confirm = JOptionPane.showConfirmDialog(
				this,
				"Reset all weapons and sound groups back to defaults?",
				"Reset All Data",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			if (confirm == JOptionPane.YES_OPTION) onReset.run();
		});
		resetRow.add(resetBtn);

		top.add(resetRow);
		top.add(Box.createVerticalStrut(6));
		top.add(buildTogglesSection());
		top.add(Box.createVerticalStrut(4));

		return top;
	}

	private JPanel buildTogglesSection()
	{
		JPanel section = new JPanel();
		section.setLayout(new javax.swing.BoxLayout(section, javax.swing.BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 6, 4, 6)
		));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton collapseBtn = new JButton("▶");
		collapseBtn.setMargin(new java.awt.Insets(2, 4, 2, 4));
		collapseBtn.setToolTipText("Expand");
		headerRow.add(collapseBtn, BorderLayout.WEST);

		JLabel headerLabel = new JLabel("Options");
		headerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 11f));
		headerRow.add(headerLabel, BorderLayout.CENTER);

		section.add(headerRow);

		JPanel content = new JPanel();
		content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setVisible(false);

		content.add(Box.createVerticalStrut(4));

		boolean ignoreSmallMaxHits = store.getBool(SfxOption.IGNORE_SMALL_MAX.configKey(), SfxOption.IGNORE_SMALL_MAX.defaultValue());
		ignoreSmallMaxCheckBox = new JCheckBox("Ignore max hits ≤ 3", ignoreSmallMaxHits);
		ignoreSmallMaxCheckBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		ignoreSmallMaxCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ignoreSmallMaxCheckBox.setToolTipText("<html>When enabled, max hit SFX will not play if the hit<br>"
			+ "damage is 3 or less. Prevents thrall max hits from<br>"
			+ "triggering max hit sounds.</html>");
		ignoreSmallMaxCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoreSmallMaxCheckBox.addActionListener(e -> onOptionToggled.accept(SfxOption.IGNORE_SMALL_MAX, ignoreSmallMaxCheckBox.isSelected()));
		content.add(ignoreSmallMaxCheckBox);

		boolean ignoreReceivedZeroWithPrayer = store.getBool(SfxOption.IGNORE_RECEIVED_ZERO_PRAYER.configKey(), SfxOption.IGNORE_RECEIVED_ZERO_PRAYER.defaultValue());
		ignoreZeroPrayerCheckBox = new JCheckBox("Ignore zeroes with prayer", ignoreReceivedZeroWithPrayer);
		ignoreZeroPrayerCheckBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		ignoreZeroPrayerCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ignoreZeroPrayerCheckBox.setToolTipText("<html>When enabled, the Received Attacks 'Regular zero'<br>"
			+ "trigger will not fire while Protect from Melee,<br>"
			+ "Protect from Ranged, or Protect from Magic is active.</html>");
		ignoreZeroPrayerCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoreZeroPrayerCheckBox.addActionListener(e -> onOptionToggled.accept(SfxOption.IGNORE_RECEIVED_ZERO_PRAYER, ignoreZeroPrayerCheckBox.isSelected()));
		content.add(ignoreZeroPrayerCheckBox);

		boolean ignoreZeroWhileThrallActive = store.getBool(SfxOption.IGNORE_ZERO_THRALL.configKey(), SfxOption.IGNORE_ZERO_THRALL.defaultValue());
		ignoreZeroThrallCheckBox = new JCheckBox("Ignore zeroes with thrall", ignoreZeroWhileThrallActive);
		ignoreZeroThrallCheckBox.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		ignoreZeroThrallCheckBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		ignoreZeroThrallCheckBox.setToolTipText("<html>When enabled, zero-damage triggers will not fire<br>"
			+ "while a thrall is active.</html>");
		ignoreZeroThrallCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		ignoreZeroThrallCheckBox.addActionListener(e -> onOptionToggled.accept(SfxOption.IGNORE_ZERO_THRALL, ignoreZeroThrallCheckBox.isSelected()));
		content.add(ignoreZeroThrallCheckBox);

		section.add(content);

		collapseBtn.addActionListener(e ->
		{
			boolean nowVisible = content.isVisible();
			content.setVisible(!nowVisible);
			collapseBtn.setText(nowVisible ? "▶" : "▼");
			collapseBtn.setToolTipText(nowVisible ? "Expand" : "Minimize");
			section.revalidate();
			section.repaint();
		});

		return section;
	}

	public void resetToggles()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (ignoreSmallMaxCheckBox != null) ignoreSmallMaxCheckBox.setSelected(true);
			if (ignoreZeroPrayerCheckBox != null) ignoreZeroPrayerCheckBox.setSelected(true);
			if (ignoreZeroThrallCheckBox != null) ignoreZeroThrallCheckBox.setSelected(true);
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
				"Received Attacks", RECEIVED_GROUPS_PREFIX, receivedGroups, availableSounds,
				EnumSet.of(Triggers.REGULAR_ZERO, Triggers.REGULAR_HIT, Triggers.ALL, Triggers.PLAYER_DEATH), SfxOption.RECEIVED_ENABLED));
			weaponListPanel.add(Box.createVerticalStrut(4));

			weaponListPanel.add(buildDefaultRowGroups(
				"Global (All Weapons)", GLOBAL_WEAPON_GROUPS_PREFIX, globalWeaponGroups, availableSounds,
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

	private JPanel buildDefaultRowGroups(String label, String prefix,
		List<TriggerGroup> groups, List<String> availableSounds, Set<Triggers> visibleTriggers,
		SfxOption enabledOption)
	{
		boolean collapsed = !expandedDefaults.contains(prefix);

		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE),
			new EmptyBorder(6, 6, 6, 6)
		));

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton collapseBtn = new JButton(collapsed ? "▶" : "▼");
		collapseBtn.setMargin(new java.awt.Insets(2, 4, 2, 4));
		collapseBtn.setToolTipText(collapsed ? "Expand" : "Minimize");
		headerRow.add(collapseBtn, BorderLayout.WEST);

		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(ColorScheme.BRAND_ORANGE);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
		headerRow.add(nameLabel, BorderLayout.CENTER);

		if (enabledOption != null)
		{
			boolean enabled = store.getBool(enabledOption.configKey(), enabledOption.defaultValue());
			JCheckBox enabledBox = new JCheckBox();
			enabledBox.setSelected(enabled);
			enabledBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			enabledBox.setToolTipText("Enable or disable this section");
			enabledBox.addActionListener(e -> onOptionToggled.accept(enabledOption, enabledBox.isSelected()));
			headerRow.add(enabledBox, BorderLayout.EAST);
		}

		panel.add(headerRow);

		Component strut = Box.createVerticalStrut(6);
		strut.setVisible(!collapsed);
		panel.add(strut);

		JPanel groupsHolder = new JPanel();
		groupsHolder.setLayout(new javax.swing.BoxLayout(groupsHolder, javax.swing.BoxLayout.Y_AXIS));
		groupsHolder.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		groupsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupsHolder.setVisible(!collapsed);
		rebuildGroupsSection(groupsHolder, groups, availableSounds,
			() -> store.saveDefaultGroups(prefix, groups), visibleTriggers);
		panel.add(groupsHolder);

		collapseBtn.addActionListener(e ->
		{
			boolean nowCollapsed = expandedDefaults.contains(prefix);
			if (nowCollapsed)
				expandedDefaults.remove(prefix);
			else
				expandedDefaults.add(prefix);
			collapseBtn.setText(nowCollapsed ? "▶" : "▼");
			collapseBtn.setToolTipText(nowCollapsed ? "Expand" : "Minimize");
			strut.setVisible(!nowCollapsed);
			groupsHolder.setVisible(!nowCollapsed);
			panel.revalidate();
			panel.repaint();
		});

		return panel;
	}

	private static final Color ROW_COLOR_A = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW_COLOR_B = new Color(40, 38, 35);

	private JPanel buildRow(WeaponEntry entry, List<String> availableSounds, int index)
	{
		Color bg = (index % 2 == 0) ? ROW_COLOR_A : ROW_COLOR_B;
		boolean collapsed = !expandedWeapons.contains(entry.getItemId());

		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBackground(bg);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(6, 6, 6, 6)
		));

		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setBackground(bg);
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JButton collapseBtn = new JButton(collapsed ? "▶" : "▼");
		collapseBtn.setMargin(new java.awt.Insets(2, 4, 2, 4));
		collapseBtn.setToolTipText(collapsed ? "Expand" : "Minimize");

		JPanel westBlock = new JPanel(new BorderLayout(4, 0));
		westBlock.setBackground(bg);
		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(32, 32));
		AsyncBufferedImage icon = itemManager.getImage(entry.getItemId());
		if (icon != null)
		{
			icon.addTo(iconLabel);
		}
		westBlock.add(collapseBtn, BorderLayout.WEST);
		westBlock.add(iconLabel, BorderLayout.EAST);
		headerRow.add(westBlock, BorderLayout.WEST);

		JPanel nameBlock = new JPanel();
		nameBlock.setLayout(new javax.swing.BoxLayout(nameBlock, javax.swing.BoxLayout.Y_AXIS));
		nameBlock.setBackground(bg);

		JLabel nameLabel = new JLabel(entry.getWeaponName());
		nameLabel.setForeground(entry.isEnabled() ? Color.WHITE : Color.GRAY);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
		nameBlock.add(Box.createVerticalGlue());
		nameBlock.add(nameLabel);
		nameBlock.add(Box.createVerticalGlue());

		headerRow.add(nameBlock, BorderLayout.CENTER);

		JPanel eastBlock = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		eastBlock.setBackground(bg);

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
		eastBlock.add(enabledBox);

		JButton removeBtn = new JButton("✕");
		removeBtn.setMargin(new java.awt.Insets(2, 5, 2, 5));
		removeBtn.setForeground(Color.RED);
		removeBtn.setToolTipText("Remove weapon");
		removeBtn.addActionListener(e ->
		{
			int confirm = JOptionPane.showConfirmDialog(
				this,
				"Remove " + entry.getWeaponName() + " and all its sound groups?",
				"Remove Weapon",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			if (confirm == JOptionPane.YES_OPTION) onRemoveWeapon.accept(entry.getItemId());
		});
		eastBlock.add(removeBtn);

		headerRow.add(eastBlock, BorderLayout.EAST);

		panel.add(headerRow);

		Component strut = Box.createVerticalStrut(6);
		strut.setVisible(!collapsed);
		panel.add(strut);

		JPanel groupsHolder = new JPanel();
		groupsHolder.setLayout(new javax.swing.BoxLayout(groupsHolder, javax.swing.BoxLayout.Y_AXIS));
		groupsHolder.setBackground(bg);
		groupsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		groupsHolder.setVisible(!collapsed);
		rebuildGroupsSection(groupsHolder, entry.getGroups(), availableSounds,
			() -> store.saveWeaponGroups(entry),
			EnumSet.complementOf(EnumSet.of(Triggers.REGULAR_HIT, Triggers.PLAYER_DEATH)));
		panel.add(groupsHolder);

		collapseBtn.addActionListener(e ->
		{
			boolean nowCollapsed = expandedWeapons.contains(entry.getItemId());
			if (nowCollapsed)
				expandedWeapons.remove(entry.getItemId());
			else
				expandedWeapons.add(entry.getItemId());
			collapseBtn.setText(nowCollapsed ? "▶" : "▼");
			collapseBtn.setToolTipText(nowCollapsed ? "Expand" : "Minimize");
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

			JPanel groupPanel = new JPanel();
			groupPanel.setLayout(new javax.swing.BoxLayout(groupPanel, javax.swing.BoxLayout.Y_AXIS));
			groupPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
			groupPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				new EmptyBorder(4, 4, 4, 4)
			));
			groupPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel groupHeader = new JPanel(new BorderLayout());
			groupHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
			groupHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

			String groupName = group.getName() != null ? group.getName() : "Sound Group " + (i + 1);
			JLabel groupLabel = new JLabel(groupName);
			groupLabel.setForeground(ColorScheme.BRAND_ORANGE);
			groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD, 11f));
			groupHeader.add(groupLabel, BorderLayout.WEST);

			JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			headerButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);

			JButton renameGroupBtn = new JButton("✎");
			renameGroupBtn.setMargin(new java.awt.Insets(2, 5, 2, 5));
			renameGroupBtn.setToolTipText("Rename sound group");
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

			JButton removeGroupBtn = new JButton("✕");
			removeGroupBtn.setMargin(new java.awt.Insets(2, 5, 2, 5));
			removeGroupBtn.setForeground(Color.RED);
			removeGroupBtn.setToolTipText("Remove sound group");
			removeGroupBtn.addActionListener(e ->
			{
				int confirm = JOptionPane.showConfirmDialog(
					this,
					"Remove \"" + groupName + "\"?",
					"Remove Sound Group",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE
				);
				if (confirm != JOptionPane.YES_OPTION) return;
				groups.remove(idx);
				onSave.run();
				rebuildGroupsSection(holder, groups, availableSounds, onSave, visibleTriggers);
			});
			headerButtons.add(removeGroupBtn);

			groupHeader.add(headerButtons, BorderLayout.EAST);

			JPanel soundsHolder = new JPanel();
			soundsHolder.setLayout(new javax.swing.BoxLayout(soundsHolder, javax.swing.BoxLayout.Y_AXIS));
			soundsHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
			soundsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
			rebuildSoundsHolder(soundsHolder, group, availableSounds, onSave);

			groupPanel.add(groupHeader);
			groupPanel.add(Box.createVerticalStrut(4));
			groupPanel.add(buildChanceRowGroup(group, onSave));
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
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

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

	private void rebuildSoundsHolder(JPanel holder, TriggerGroup group,
		List<String> availableSounds, Runnable onSave)
	{
		holder.removeAll();
		List<SoundEntry> sounds = group.getSounds();

		for (int j = 0; j < sounds.size(); j++)
		{
			final int idx = j;
			SoundEntry se = sounds.get(j);

			JPanel soundRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			soundRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			soundRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel lbl = new JLabel("Sound:");
			lbl.setForeground(Color.WHITE);
			lbl.setToolTipText("Sound file to play. Built-in sounds are bundled with the plugin; custom sounds load from .runelite/customweaponsfx/");
			soundRow.add(lbl);

			String[] options = buildSoundOptions(availableSounds);
			JComboBox<String> box = new JComboBox<>(options);
			box.setPreferredSize(new Dimension(85, box.getPreferredSize().height));
			box.setSelectedItem(configToDisplay(se.getSoundFile()));
			box.addActionListener(e ->
			{
				se.setSoundFile(displayToConfig((String) box.getSelectedItem()));
				onSave.run();
			});
			soundRow.add(box);

			JButton testBtn = new JButton("▶");
			testBtn.setMargin(new java.awt.Insets(2, 5, 2, 5));
			testBtn.setToolTipText("Test sound");
			testBtn.addActionListener(e -> onTestSound.accept(
				displayToConfig((String) box.getSelectedItem()), se.getVolume()));
			soundRow.add(testBtn);

			if (sounds.size() > 1)
			{
				JButton removeBtn = new JButton("✕");
				removeBtn.setMargin(new java.awt.Insets(2, 4, 2, 4));
				removeBtn.setForeground(Color.RED);
				removeBtn.setToolTipText("Remove this sound");
				removeBtn.addActionListener(e ->
				{
					sounds.remove(idx);
					onSave.run();
					rebuildSoundsHolder(holder, group, availableSounds, onSave);
				});
				soundRow.add(removeBtn);
			}

			holder.add(soundRow);
			holder.add(buildVolumeSoundEntry(se, onSave));

			if (j < sounds.size() - 1)
				holder.add(Box.createVerticalStrut(4));
		}

		holder.add(Box.createVerticalStrut(2));
		JButton addSoundBtn = new JButton("+ Add Sound");
		addSoundBtn.setToolTipText("Add another sound to this group — one will be picked at random when the group fires");
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

	private JPanel buildVolumeSoundEntry(SoundEntry se, Runnable onSave)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel lbl = new JLabel("Volume:");
		lbl.setForeground(Color.WHITE);
		lbl.setToolTipText("Playback volume for this sound (0 = silent, 100 = full volume)");
		row.add(lbl);
		JSlider slider = new JSlider(0, 100, se.getVolume());
		slider.setPreferredSize(new Dimension(100, 20));
		JLabel val = new JLabel(se.getVolume() + "%");
		val.setForeground(Color.LIGHT_GRAY);
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			val.setText(v + "%");
			if (!slider.getValueIsAdjusting())
			{
				se.setVolume(v);
				onSave.run();
			}
		});
		row.add(slider);
		row.add(val);
		return row;
	}

	private JPanel buildChanceRowGroup(TriggerGroup group, Runnable onSave)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel lbl = new JLabel("Chance:");
		lbl.setForeground(Color.WHITE);
		lbl.setToolTipText("Probability that this sound group plays when its trigger fires (100 = always, 0 = never)");
		row.add(lbl);
		JSlider slider = new JSlider(0, 100, group.getChance());
		slider.setPreferredSize(new Dimension(100, 20));
		JLabel val = new JLabel(group.getChance() + "%");
		val.setForeground(Color.LIGHT_GRAY);
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			val.setText(v + "%");
			if (!slider.getValueIsAdjusting())
			{
				group.setChance(v);
				onSave.run();
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
}
