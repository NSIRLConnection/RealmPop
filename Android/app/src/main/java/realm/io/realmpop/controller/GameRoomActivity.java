package realm.io.realmpop.controller;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.RecyclerView;

import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import realm.io.realmpop.R;
import realm.io.realmpop.model.GameModel;
import realm.io.realmpop.model.realm.Game;
import realm.io.realmpop.model.realm.Player;
import realm.io.realmpop.model.realm.Side;
import realm.io.realmpop.util.BubbleConstants;

import static realm.io.realmpop.R.style.AppTheme_RealmPopDialog;
import static realm.io.realmpop.util.RandomUtils.generateNumbersArray;

public class GameRoomActivity extends AppCompatActivity {

    private Realm realm;

    private GameModel gameModel;
    private Player me;
    RealmResults<Player> otherPlayers;

    @BindView(R.id.player_list)
    public RecyclerView recyclerView;

    private PlayerRecyclerViewAdapter adapter;

    private AtomicBoolean inGame = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gameroom);
        ButterKnife.bind(this);

        realm = Realm.getDefaultInstance();

        gameModel = new GameModel(realm);
        me = gameModel.currentPlayer();
        otherPlayers = realm.where(Player.class)
                            .equalTo("available", true)
                            .notEqualTo("id", me.getId())
                            .findAllSortedAsync("name");
        recyclerView.setAdapter(new PlayerRecyclerViewAdapter(this, otherPlayers));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        me.addChangeListener(new RealmChangeListener<Player>() {
            @Override
            public void onChange(Player myself) {
                if(!inGame.get()) {
                    if(myself.getChallenger() != null) {
                        handleInvite(myself.getChallenger());
                    }
                    if(myself.getCurrentgame() != null) {
                        moveToGame();
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setMyAvailability(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        setMyAvailability(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(realm != null) {
            realm.removeAllChangeListeners();
            realm.close();
            realm = null;
        }
        gameModel = null;
    }

    private void handleInvite(final Player challenger) {

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        createGame(challenger);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(Realm realm) {
                                me.setChallenger(null);
                            }
                        });
                        break;
                }
            }
        };

        ContextThemeWrapper themedContext = new ContextThemeWrapper( this, AppTheme_RealmPopDialog );
        AlertDialog.Builder builder = new AlertDialog.Builder(themedContext);
        builder.setMessage("You were invited to a game by " + challenger.getName() + "")
                .setPositiveButton("Accept", dialogClickListener)
                .setNegativeButton("No, thanks", dialogClickListener)
                .show();
    }

    public void challengePlayer(final Player player) {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                player.setChallenger(me);
            }
        });
    }

    private void createGame(final Player challenger) {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {

                Game game = realm.createObject(Game.class);

                int [] numbers = generateNumbersArray(BubbleConstants.bubbleCount, 1, 80);
                game.setNumberArray(numbers);

                Side player1 = new Side();
                player1.setName(me.getName());
                player1.setLeft(numbers.length);
                player1 = realm.copyToRealm(player1);
                game.setPlayer1(player1);

                Side player2 = new Side();
                player2.setName(challenger.getName());
                player2.setLeft(numbers.length);
                player2 = realm.copyToRealm(player2);
                game.setPlayer2(player2);

                me.setCurrentgame(game);
                challenger.setCurrentgame(game);
            }
        });
    }

    private void moveToGame() {
        if(inGame.compareAndSet(false, true)) {
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra(Player.class.getName(), me.getId());
            startActivityForResult(intent, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        inGame.set(false);
    }

    private void setMyAvailability(final boolean isAvail) {
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                me.setAvailable(isAvail);
            }
        });
    }
}